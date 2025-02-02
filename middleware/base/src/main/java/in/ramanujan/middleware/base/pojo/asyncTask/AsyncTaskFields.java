package in.ramanujan.middleware.base.pojo.asyncTask;

public enum AsyncTaskFields {
    uuid("taskId", (task, object) ->{task.setTaskId((String) object);}),
    taskStatus("taskStatus", ((task, update) -> {task.setTaskStatus((AsyncTask.TaskStatus) update);})),
    result("result", ((task, update) -> {task.setResult(update);}));


    private String fieldName;
    private AsyncTaskFieldsUpdator asyncTaskFieldsUpdator;

    AsyncTaskFields(String fieldName, AsyncTaskFieldsUpdator asyncTaskFieldsUpdator) {
        this.fieldName = fieldName;
        this.asyncTaskFieldsUpdator = asyncTaskFieldsUpdator;
    }

    public void triggerUpdate(AsyncTask asyncTask, Object objectUpdated) {
        asyncTaskFieldsUpdator.updateAsyncTaskFields(asyncTask, objectUpdated);
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public static AsyncTaskFields getAsyncTaskFields(String key) {
        for(AsyncTaskFields asyncTaskFields : values()) {
            if(asyncTaskFields.getFieldName().equalsIgnoreCase(key)) {
                return asyncTaskFields;
            }
        }
        return null;
    }


    private static interface AsyncTaskFieldsUpdator {
        public void updateAsyncTaskFields(AsyncTask task, Object update);
    }
}
