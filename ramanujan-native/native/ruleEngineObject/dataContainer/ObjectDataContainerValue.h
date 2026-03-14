//
// ObjectDataContainerValue.h
//

#ifndef NATIVE_OBJECTDATACONTAINERVALUE_H
#define NATIVE_OBJECTDATACONTAINERVALUE_H

#include "DataContainerValue.h"
#include "DataContainerValueFunctionCommandRE.h"

// Forward declaration – defined in ObjectInstanceRE.h
class ObjectInstanceRE;

/**
 * DataContainerValue implementation for class-object arguments.
 *
 * This is the DataContainerValue analog of DoublePtr (for scalars) and
 * ArrayDataContainerValue (for arrays), but for class objects.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * COPY-BY-REFERENCE SEMANTICS FOR OBJECTS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * A class object consists of N field VariableREs (one per declared field).
 * Those VariableREs are allocated once at object-creation time and are
 * identified by the ObjectInstanceRE that owns them.
 *
 * When an object is passed as a function argument, FunctionCommandRE links the
 * callee's ObjectInstanceRE to the same VariableREs as the caller's:
 *
 *   saveValueAndCopyFrom(saved, callerObjDataContainerValue):
 *     1. Saves the callee's current ObjectInstanceRE* in saved->objectPtr.
 *     2. Replaces the callee's ObjectInstanceRE* with the caller's — so that
 *        any field access inside the callee goes to the caller's VariableREs.
 *
 *   saveRestoreAndPropagate(saved, callerObjDataContainerValue):
 *     1. Restores the callee's original ObjectInstanceRE* from saved->objectPtr.
 *     2. Does NOT propagate any value: because both caller and callee have been
 *        operating on the SAME VariableREs throughout the call, all mutations
 *        are already visible to the caller — true copy-by-reference.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class ObjectDataContainerValue : public DataContainerValue {
    /** The ObjectInstanceRE this DataContainerValue represents. */
    ObjectInstanceRE* objectInstanceRE = nullptr;

public:
    ObjectDataContainerValue() = default;

    explicit ObjectDataContainerValue(ObjectInstanceRE* re) : objectInstanceRE(re) {}

    // ── Accessors ──────────────────────────────────────────────────────────

    ObjectInstanceRE* getObjectInstanceRE() const { return objectInstanceRE; }
    void setObjectInstanceRE(ObjectInstanceRE* re) { objectInstanceRE = re; }

    // ── DataContainerValue interface ───────────────────────────────────────

    /**
     * Restores the ObjectInstanceRE pointer from a previously saved slot.
     * Called by FunctionCommandRE during local-variable restoration.
     * Note: objectPtr may be nullptr if this object was never passed as an argument;
     *       setting objectInstanceRE to nullptr is a valid "uninitialised" state.
     */
    void copyDataContainerValueFunctionCommandRE(
            DataContainerValueFunctionCommandRE* toBeCopied) override {
        objectInstanceRE = static_cast<ObjectInstanceRE*>(toBeCopied->objectPtr);
    }

    /**
     * Stores the current ObjectInstanceRE pointer into a save slot.
     * Called by FunctionCommandRE to save state before parameter setup.
     */
    void setValueInDataContainerValueFunctionCommandRE(
            DataContainerValueFunctionCommandRE* toBeSet) override {
        toBeSet->objectPtr = objectInstanceRE;
    }

    /**
     * Implements by-reference parameter passing for objects.
     *
     * 1. Saves the callee's current ObjectInstanceRE* in {@p savedValue->objectPtr}.
     * 2. Sets this container's ObjectInstanceRE* to the caller's ObjectInstanceRE*
     *    (via {@p source}), so the callee and caller share the same field VariableREs.
     *
     * Precondition: {@p source} must be an ObjectDataContainerValue; callers in
     * FunctionCommandRE always pair an ObjectInstanceRE callee parameter with an
     * ObjectInstanceRE calling argument, so this invariant is guaranteed.
     *
     * @param savedValue  Save slot for the callee's original object reference.
     * @param source      The caller's ObjectDataContainerValue.
     */
    void saveValueAndCopyFrom(DataContainerValueFunctionCommandRE* savedValue,
                              DataContainerValue* source) override {
        // 1. Save callee's original ObjectInstanceRE* for later restoration.
        savedValue->objectPtr = objectInstanceRE;
        // 2. Share the caller's ObjectInstanceRE* — all field accesses in the
        //    callee now target the caller's VariableREs (copy-by-reference).
        //    source is guaranteed to be ObjectDataContainerValue by FunctionCommandRE.
        objectInstanceRE = static_cast<ObjectDataContainerValue*>(source)->objectInstanceRE;
    }

    /**
     * Saves the current ObjectInstanceRE* and restores from a previously saved slot.
     * Note: restoreFrom->objectPtr may be nullptr (initial state); this is valid.
     */
    void saveValueAndRestoreFrom(DataContainerValueFunctionCommandRE& savedValue,
                                 DataContainerValueFunctionCommandRE* restoreFrom) override {
        savedValue.objectPtr = objectInstanceRE;
        objectInstanceRE = static_cast<ObjectInstanceRE*>(restoreFrom->objectPtr);
    }

    /**
     * Restores the callee's original ObjectInstanceRE* and performs no value
     * propagation (objects are always passed by reference).
     *
     * Because caller and callee have shared the same field VariableREs throughout
     * the call, the caller already sees every mutation made inside the callee —
     * no separate propagation step is needed.
     *
     * Note: restoreFrom->objectPtr may be nullptr (initial state); this is valid.
     *
     * @param restoreFrom  The save slot holding the callee's original object ref.
     * @param propagateTo  The caller's ObjectDataContainerValue (ignored).
     */
    void saveRestoreAndPropagate(DataContainerValueFunctionCommandRE* restoreFrom,
                                 DataContainerValue* propagateTo) override {
        // Restore callee's original ObjectInstanceRE*.
        objectInstanceRE = static_cast<ObjectInstanceRE*>(restoreFrom->objectPtr);
        // No value propagation: mutations are already in the caller's VariableREs.
        (void)propagateTo;
    }
};

#endif // NATIVE_OBJECTDATACONTAINERVALUE_H
