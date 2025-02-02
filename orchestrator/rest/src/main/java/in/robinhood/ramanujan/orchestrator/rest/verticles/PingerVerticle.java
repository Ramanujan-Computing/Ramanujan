package in.robinhood.ramanujan.orchestrator.rest.verticles;

import in.robinhood.ramanujan.orchestrator.base.DateTimeUtils;
import in.robinhood.ramanujan.orchestrator.base.EventBus;
import in.robinhood.ramanujan.orchestrator.base.enums.AsyncTaskFields;
import in.robinhood.ramanujan.orchestrator.base.enums.Status;
import in.robinhood.ramanujan.orchestrator.base.pojo.AsyncTask;
import in.robinhood.ramanujan.orchestrator.base.pojo.HeartBeat;
import in.robinhood.ramanujan.orchestrator.data.dao.AsyncTaskDao;
import in.robinhood.ramanujan.orchestrator.data.dao.HeartBeatDao;
import in.robinhood.ramanujan.orchestrator.data.dao.HostsDao;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class PingerVerticle extends AbstractVerticle {

    private Logger logger= LoggerFactory.getLogger(PingerVerticle.class);

    @Autowired
    private HeartBeatDao heartBeatDao;

    @Autowired
    private AsyncTaskDao asyncTaskDao;

    @Autowired
    private HostsDao hostsDao;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        logger.info("PingerVerticle started");
        vertx.eventBus().consumer(EventBus.PINGER, message -> {
            AsyncTask asyncTask = ((JsonObject) message.body()).mapTo(AsyncTask.class);
            heartBeatDao.getLastHeartBeat(asyncTask.getHostAssigned()).setHandler(heartBeatHandler -> {
                if(heartBeatHandler.succeeded()) {
                    HeartBeat heartBeat = heartBeatHandler.result();
                    if(heartBeat != null && heartBeat.getData() != null) {
                        //Computation has got over
                        JsonObject updateQuery = new JsonObject()
                                .put(AsyncTaskFields.status.getFieldName(), Status.SUCCESS);
                        logger.info("Computation done for " + asyncTask.getUuid());
                        asyncTaskDao.update(asyncTask.getUuid(), updateQuery.getMap()).setHandler(asyncTaskUpdateHandler -> {
                            if(asyncTaskUpdateHandler.succeeded()) {
                                logger.info("AsyncTask updated for " + asyncTask.getUuid());
                                message.reply(heartBeat.getData());
                            } else {
                                logger.error("AsyncTask update failed for " + asyncTask.getUuid(),
                                        asyncTaskUpdateHandler.cause());
                                message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), asyncTaskUpdateHandler
                                        .cause().getMessage());
                            }
                        });
                    } else {
                        if(heartBeat == null || (new Date().toInstant().toEpochMilli() - heartBeat.getHeartBeatTimeEpoch()) > DateTimeUtils.maxHeartBeatDiff) {
                            //current host has become non-responsive
                            //need to get a new host and restart computation
                            getNewHostAndStartComputation(asyncTask, message);
                        } else {
                            //current host is up and running, need to keep pinging
                            logger.info("Computation still running for " + asyncTask.getUuid());
                            vertx.setTimer(500, timerHandler -> {
                                asyncTaskDao.getAsyncTask(asyncTask.getUuid()).setHandler(asyncTaskHandler -> {
                                    if(!asyncTaskHandler.succeeded() || !Status.SUCCESS.getKeyName().
                                            equalsIgnoreCase(asyncTaskHandler.result().getStatus())) {
                                        sendMessageOnPingerVerticle(asyncTask);
                                    }
                                });
                            });
                            message.reply("in process");
                        }
                    }
                } else {
                    logger.error("Some error getting hearbeat for " + asyncTask, heartBeatHandler.cause());
                    message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), heartBeatHandler.cause().getMessage());
                }
            });
        });
    }

    private void sendMessageOnPingerVerticle(AsyncTask asyncTask) {
        asyncTaskDao.getAsyncTask(asyncTask.getUuid()).setHandler(handler -> {
           if(handler.failed() || Status.PROCESSING.getKeyName().equalsIgnoreCase(handler.result().getStatus())) {
               vertx.eventBus().publish(EventBus.PINGER, JsonObject.mapFrom(asyncTask));
           }
        });
    }

    private void getNewHostAndStartComputation(AsyncTask asyncTask, Message<Object> message) {
        hostsDao.getMachine(asyncTask,true).setHandler(hostMachineGetter -> {
            if(hostMachineGetter.succeeded()) {
                JsonObject updateQuery = new JsonObject();
//                        .put(AsyncTaskFields.hostAssigned.getFieldName(), hostMachineGetter.result());
                asyncTaskDao.update(asyncTask.getUuid(), updateQuery.getMap()).setHandler(asyncTaskUpdateHandler -> {
                    if(asyncTaskUpdateHandler.succeeded()) {
                        logger.info(asyncTask.getUuid() + " got a new machine " + hostMachineGetter.result());
                        sendMessageOnPingerVerticle(asyncTask);
                        message.reply("pinging");
                    } else {
                        logger.error(asyncTask.getUuid() + " couldnt update in asyncTaskDataSource", asyncTaskUpdateHandler.cause());
                        message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), asyncTaskUpdateHandler.cause().getMessage());
                    }
                });

            } else {
                logger.error(asyncTask.getUuid() + " Couldn't get a machine", hostMachineGetter.cause());
                message.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), hostMachineGetter.cause().getMessage());
            }
        });
    }
}
