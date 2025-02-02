package in.robinhood.ramanujan.middleware.service.future;

import io.vertx.core.Future;

public abstract class FutureCaller {
    protected final FutureCaller nextCaller;

    public FutureCaller(FutureCaller nextCaller) {
        this.nextCaller = nextCaller;
    }

    abstract Future<Void> callInternal();

    public final Future<Void> call() {
        Future<Void> future = Future.future();
        callInternal().setHandler(handler -> {
            if(handler.succeeded()) {
                if(nextCaller == null) {
                    future.complete();
                    return;
                }
                nextCaller.call().setHandler(nextCallerHandler -> {
                   if(nextCallerHandler.succeeded()) {
                       future.complete();
                   } else {
                       future.fail(nextCallerHandler.cause());
                   }
                });
            } else {
                future.fail(handler.cause());
            }
        });
        return future;
    }
}
