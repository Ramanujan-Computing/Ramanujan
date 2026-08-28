//
// Created by Pranav on 09/06/24.
//

#include "ArrayValue.h"
#include "../DataContainerValueFunctionCommandRE.h"

#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>


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
            int fd = open(key.c_str(), O_RDONLY);
            if (fd >= 0) {
                struct stat st;
                fstat(fd, &st);
                size_t fileSize = st.st_size;
                fcount = (int)(fileSize / sizeof(float));
                if (fcount > totalSize) fcount = totalSize;

                size_t mapSize = totalSize * sizeof(float);
                if (mapSize == 0) mapSize = sizeof(float);

                // Two-step mmap: anonymous region covers the full totalSize (tail past
                // fcount is demand-zeroed, avoiding SIGBUS on a short file); MAP_FIXED
                // overlays the file on the first fcount floats. The overlay is file-backed
                // and read-only, so its pages are reclaimable page cache the kernel can
                // evict under memory pressure -- essential for the ~2.7 GB of Phi-3 weights
                // on a 3.6 GB device. clCreateBuffer(CL_MEM_COPY_HOST_PTR) copies via a
                // CPU memcpy, which faults these pages in normally (no GPU-DMA race).
                void* mapped = mmap(nullptr, mapSize, PROT_READ | PROT_WRITE,
                                    MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
                if (mapped != MAP_FAILED) {
                    if (fcount > 0) {
                        mmap(mapped, (size_t)fcount * sizeof(float), PROT_READ,
                             MAP_PRIVATE | MAP_FIXED, fd, 0);
                    }
                    fdata = static_cast<float*>(mapped);
                }
                close(fd);
            }
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
            int* index = getIndexFromStr(key, dimensionSize);
            add(index, value);
            delete[] index;
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
