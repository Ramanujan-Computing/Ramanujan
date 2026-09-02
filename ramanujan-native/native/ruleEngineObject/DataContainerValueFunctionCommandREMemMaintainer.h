//
// Created by pranav on 9/11/24.
//

#ifndef NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDREMEMMAINTAINER_H
#define NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDREMEMMAINTAINER_H

#include "dataContainer/DataContainerValueFunctionCommandRE.h"

/**
 * Memory Maintainer for DataContainerValueFunctionCommandRE Objects
 * 
 * This class maintains a memory pool of DataContainerValueFunctionCommandRE objects
 * to avoid repeated allocations and deallocations during function calls.
 * 
 * Key Features:
 * - Pre-allocates objects on demand
 * - Reuses previously allocated objects via stack-based management
 * - Zero-copy allocation by returning direct pointers into the pool
 * - Thread-unsafe (designed for single-threaded rule engine execution)
 * 
 * Performance Benefits:
 * - Eliminates allocation overhead during function calls
 * - Improves cache locality through contiguous memory
 * - Reduces memory fragmentation
 * 
 * Usage Pattern:
 * 1. Call allocateDual(n) to request n objects
 * 2. Use the returned pointer range for computation
 * 3. Call deallocateDual(n) to return objects to pool
 */
class DataContainerValueFunctionCommandREMemMaintainer {
private:
    /**
     * Memory pool array storing pre-allocated DataContainerValueFunctionCommandRE objects.
     * Size: 256K objects (sufficient for deeply nested function calls)
     */
    DataContainerValueFunctionCommandRE* memStack[256 * 1000];
    
    /**
     * Total number of objects created so far (grows on demand).
     * Never decreases - objects are reused, not destroyed.
     */
    int memStackSizePtrCreated = 0;
    
    /**
     * Current stack pointer - number of objects currently in use.
     * Objects from 0 to (currentIter-1) are in use.
     * Objects from currentIter to (memStackSizePtrCreated-1) are available.
     */
    int currentIter = 0;
    
    /**
     * Temporary variable for allocation calculations.
     * Stores the number of new objects that need to be created.
     */
    int needed;

public:
    /**
     * Pointer to the start of the currently allocated range.
     * After allocateDual(), this points to the first object in the allocated range.
     */
    DataContainerValueFunctionCommandRE** currentAsk;
    
    /**
     * Allocates a contiguous range of DataContainerValueFunctionCommandRE objects.
     * 
     * This method returns a direct pointer into the memory pool without any copying.
     * If insufficient objects exist in the pool, new objects are created on demand.
     * 
     * Performance Optimization:
     * - Uses __builtin_expect to hint that allocation is rare (branch prediction)
     * - Returns pointers directly into the stack (zero-copy allocation)
     * - Amortizes allocation cost over multiple function calls
     * 
     * @param totalSize Number of DataContainerValueFunctionCommandRE objects needed
     * 
     * Side Effects:
     * - Updates currentAsk to point to the allocated range
     * - Advances currentIter by totalSize
     * - May create new objects if pool is insufficient
     * 
     * Example:
     * ```cpp
     * maintainer.allocateDual(10);
     * DataContainerValueFunctionCommandRE** objects = maintainer.currentAsk;
     * // Use objects[0] through objects[9]
     * maintainer.deallocateDual(10);
     * ```
     */
    inline void allocateDual(int totalSize) {
        if((memStackSizePtrCreated - currentIter) < totalSize) {
            needed = totalSize - (memStackSizePtrCreated - currentIter);
            for(int i = 0; i < needed; i++) {
                memStack[memStackSizePtrCreated++] = new DataContainerValueFunctionCommandRE();
            }
        }

        // Return pointers directly into the stack - no memcpy needed!
        currentAsk = &memStack[currentIter];

        // Advance the stack pointer
        currentIter += totalSize;
    }

    /**
     * Returns a range of objects back to the memory pool.
     * 
     * This operation is extremely fast - it simply decrements the stack pointer.
     * The objects remain allocated and will be reused for future allocations.
     * 
     * @param totalSizeDeAllocated Number of objects to return to the pool
     * 
     * Example:
     * ```cpp
     * maintainer.allocateDual(10);
     * // Use the allocated objects
     * maintainer.deallocateDual(10);  // Return them to pool
     * ```
     */
    inline void deallocateDual(int totalSizeDeAllocated) {
        currentIter -= totalSizeDeAllocated;
    }
    
    /**
     * Destructor - cleans up all allocated objects.
     * Called when the maintainer is destroyed (end of processing session).
     */
    ~DataContainerValueFunctionCommandREMemMaintainer() {
        for(int i = 0; i < memStackSizePtrCreated; i++) {
            delete memStack[i];
        }
    }
};

#endif //NATIVE_DATACONTAINERVALUEFUNCTIONCOMMANDREMEMMAINTAINER_H
