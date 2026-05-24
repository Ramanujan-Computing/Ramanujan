//
// Created by Pranav on 09/06/24.
//

#include "ArrayValue.h"
#include "../DataContainerValueFunctionCommandRE.h"

#ifdef __ANDROID__
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#endif


// Global cache: binaryFilePath -> {float* data, int count}
static std::mutex                                              s_binaryMutex;
static std::unordered_map<std::string, std::pair<float*, int>> s_binaryCache;

ArrayValue::ArrayValue(Array* array , std::string originalArrayId) {
    this->array = array;

    dimensionSize = array->dimensionSize;
    dimensions = new int[dimensionSize];
    if(dimensionSize == 0) {
        return;
    }

    if(dimensionSize > 0) {
        sizeAtIndex = new int[dimensionSize];
    }
    int i = 0;
    for (int dim : array->dimension) {
        sizeAtIndex[i] = -1;
        dimensions[i++] = dim;
    }

    totalSize = getTotalSize(dimensions, 0, dimensionSize);

    // Fast path: load from binary float32 file directly into val[]
    if (!array->binaryFile.empty()) {
        // Check cache first (avoids re-reading disk on every kernel call)
        std::string key = array->binaryFile;
        float* fdata = nullptr;
        int fcount = 0;
        {
            std::lock_guard<std::mutex> lk(s_binaryMutex);
            auto it = s_binaryCache.find(key);
            if (it != s_binaryCache.end()) {
                fdata  = it->second.first;
                fcount = it->second.second;
            }
        }
        if (!fdata) {
            // First time: read from disk, insert into cache
            int fd = -1;
#ifdef __ANDROID__
            fd = open(key.c_str(), O_RDONLY);
            if (fd >= 0) {
                struct stat st;
                fstat(fd, &st);
                size_t fileSize = st.st_size;
                fcount = (int)(fileSize / sizeof(float));
                if (fcount > totalSize) fcount = totalSize;
                
                size_t mapSize = totalSize * sizeof(float);
                if (mapSize == 0) mapSize = sizeof(float); // Avoid 0 size mmap
                
                // Use mmap to avoid OOM for large weight files
                void* mapped = mmap(nullptr, mapSize, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);
                if (mapped == MAP_FAILED) {
                    std::cerr << "[ArrayValue] mmap failed for: " << key << std::endl;
                    // Fallback to allocation
                    if (ALIGNED_ALLOC(&fdata, 4096, totalSize * sizeof(float)) == 0 && fdata != nullptr) {
                        memset(fdata, 0, totalSize * sizeof(float));
                        read(fd, fdata, fcount * sizeof(float));
                    } else {
                        fdata = nullptr;
                    }
                } else {
                    fdata = static_cast<float*>(mapped);
                }
                close(fd);
            }
#else
            std::ifstream file(key, std::ios::binary | std::ios::ate);
            if (file.is_open()) {
                size_t fileSize = file.tellg();
                file.seekg(0, std::ios::beg);
                fcount = (int)(fileSize / sizeof(float));
                if (fcount > totalSize) fcount = totalSize;
                
                if (ALIGNED_ALLOC(&fdata, 4096, totalSize * sizeof(float)) == 0 && fdata != nullptr) {
                    memset(fdata, 0, totalSize * sizeof(float));
                    file.read(reinterpret_cast<char*>(fdata), fcount * sizeof(float));
                } else {
                    fdata = nullptr;
                }
                file.close();
            }
#endif
            if (!fdata) {
                std::cerr << "[ArrayValue] Failed to open/allocate for binary file: " << key << std::endl;
                if (ALIGNED_ALLOC(&fdata, 4096, totalSize * sizeof(float)) == 0 && fdata != nullptr) {
                    memset(fdata, 0, totalSize * sizeof(float));
                }
            }
            if (fdata) {
                std::lock_guard<std::mutex> lk(s_binaryMutex);
                s_binaryCache[key] = {fdata, fcount};
            }
        }
        // val[] is now a direct pointer to the static cache (ZERO COPY)
        val = fdata;
        isBinaryLoaded  = true;
        isCachedVal = true;
        cachedFloatData = fdata;
    } else {
        ALIGNED_ALLOC(&val, 4096, totalSize * sizeof(float));
        memset(val, 0, totalSize * sizeof(float));
        
        // Slow path: parse string-keyed map
        for(auto & it : array->values) {
            std::string key = it.first;
            double value = it.second;
            //TODO: check if the size is faring correct.
            add(getIndexFromStr(key, dimensionSize), value);
        }
    }
}

void ArrayValue::add(int* index, double value) {
    int indexInt = translateIndex(index);
    val[indexInt] = (float)value;
}

void ArrayDataContainerValue::copyDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE* toBeCopied) {
    //delete arrayValue;
    //TODO: pranav: check if this is causing memory leak
    arrayValue->val = toBeCopied->arrayValuePtr;
}

void ArrayDataContainerValue::setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE* toBeSet) {
    // Clean up current array value if present
    toBeSet->arrayValuePtr = arrayValue->val;
}

void ArrayDataContainerValue::saveValueAndCopyFrom(DataContainerValueFunctionCommandRE* savedValue, DataContainerValue* source) {
    // Save current value
    savedValue->arrayValuePtr = arrayValue->val;
    oldValue = arrayValue;
    // Copy from source
    arrayValue = new ArrayValue(((ArrayDataContainerValue*) source)->arrayValue, true);
    delete oldValue;
}

void ArrayDataContainerValue::saveValueAndRestoreFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValueFunctionCommandRE* restoreFrom) {
    // Save current value
    savedValue.arrayValuePtr = arrayValue->val;
    // Restore from saved value

    arrayValue->val = restoreFrom->arrayValuePtr;
}

void ArrayDataContainerValue::saveRestoreAndPropagate(DataContainerValueFunctionCommandRE* restoreFrom, DataContainerValue* propagateTo) {
    // Save current value (final computed result)
    placeholder = arrayValue->val;
    // Restore from previous saved value
    arrayValue->val = restoreFrom->arrayValuePtr;
    // Propagate final value to calling context
    ((ArrayDataContainerValue*)propagateTo)->arrayValue->val = placeholder;
}
