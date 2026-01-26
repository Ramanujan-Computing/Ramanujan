package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(
        ignoreUnknown = true
)
public class ReturnAssignmentPair {
    private String targetCommandId;
    private String sourceCommandId;

    public String getTargetCommandId() {
        return this.targetCommandId;
    }

    public String getSourceCommandId() {
        return this.sourceCommandId;
    }

    public void setTargetCommandId(final String targetCommandId) {
        this.targetCommandId = targetCommandId;
    }

    public void setSourceCommandId(final String sourceCommandId) {
        this.sourceCommandId = sourceCommandId;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ReturnAssignmentPair)) {
            return false;
        } else {
            ReturnAssignmentPair other = (ReturnAssignmentPair)o;
            if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$targetCommandId = this.getTargetCommandId();
                Object other$targetCommandId = other.getTargetCommandId();
                if (this$targetCommandId == null) {
                    if (other$targetCommandId != null) {
                        return false;
                    }
                } else if (!this$targetCommandId.equals(other$targetCommandId)) {
                    return false;
                }

                Object this$sourceCommandId = this.getSourceCommandId();
                Object other$sourceCommandId = other.getSourceCommandId();
                if (this$sourceCommandId == null) {
                    if (other$sourceCommandId != null) {
                        return false;
                    }
                } else if (!this$sourceCommandId.equals(other$sourceCommandId)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ReturnAssignmentPair;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $targetCommandId = this.getTargetCommandId();
        result = result * 59 + ($targetCommandId == null ? 43 : $targetCommandId.hashCode());
        Object $sourceCommandId = this.getSourceCommandId();
        result = result * 59 + ($sourceCommandId == null ? 43 : $sourceCommandId.hashCode());
        return result;
    }

    public String toString() {
        return "ReturnAssignmentPair(targetCommandId=" + this.getTargetCommandId() + ", sourceCommandId=" + this.getSourceCommandId() + ")";
    }
}