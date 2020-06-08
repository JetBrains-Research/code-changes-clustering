package io.mewbase.server.impl;

import io.mewbase.server.MewbaseAuthProvider;
import io.mewbase.server.MewbaseUser;
import io.mewbase.bson.BsonArray;
import io.mewbase.bson.BsonObject;
import io.mewbase.client.Client;
import io.mewbase.common.SubDescriptor;
import io.mewbase.server.Binder;
import io.mewbase.server.DocReadStream;
import io.mewbase.server.Log;
import io.mewbase.server.impl.auth.UnauthorizedUser;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by tim on 23/09/16.
 */
public class ConnectionImpl implements ServerFrameHandler {

    private final static Logger logger = LoggerFactory.getLogger(ConnectionImpl.class);

    private final ServerImpl server;
    private final TransportConnection transportConnection;
    private final Context context;
    private final Map<Integer, SubscriptionImpl> subscriptionMap = new HashMap<>();
    private final Map<Integer, QueryState> queryStates = new HashMap<>();

    private boolean closed;
    private MewbaseAuthProvider authProvider;
    private MewbaseUser user;
    //private boolean authenticated;
    private int subSeq;

    public ConnectionImpl(ServerImpl server, TransportConnection transportConnection, Context context,
                          MewbaseAuthProvider authProvider) {
        Protocol protocol = new Protocol(this);
        RecordParser recordParser = protocol.recordParser();
        transportConnection.handler(recordParser::handle);
        this.server = server;
        this.transportConnection = transportConnection;
        this.context = context;
        this.authProvider = authProvider;
        transportConnection.closeHandler(this::close);
    }

    @Override
    public void handleConnect(BsonObject frame) {
        checkContext();

        BsonObject value = (BsonObject)frame.getValue(Protocol.CONNECT_AUTH_INFO);
        CompletableFuture<MewbaseUser> cf = authProvider.authenticate(value);

        cf.handle((result, ex) -> {

            checkContext();
            BsonObject response = new BsonObject();
            if (ex != null) {
                sendErrorResponse(Client.ERR_AUTHENTICATION_FAILED, "Authentication failed");
                logAndClose(ex.getMessage());
            } else {
                if (result != null) {
                    user = result;
                    response.put(Protocol.RESPONSE_OK, true);
                    writeResponse(Protocol.RESPONSE_FRAME, response);
                } else {
                    String nullUserMsg = "AuthProvider returned a null user";
                    logAndClose(nullUserMsg);
                    throw new IllegalStateException(nullUserMsg);
                }
            }
            return null;
        });
        // TODO version checking
    }

    @Override
    public void handlePublish(BsonObject frame) {
        checkContext();

        //TODO: how to apply this correctly?
        CompletableFuture<Boolean> authorisedCF = user.isAuthorised(Protocol.PUBLISH_FRAME);

        String channel = frame.getString(Protocol.PUBLISH_CHANNEL);
        BsonObject event = frame.getBsonObject(Protocol.PUBLISH_EVENT);
        Integer sessID = frame.getInteger(Protocol.PUBLISH_SESSID);
        Integer requestID = frame.getInteger(Protocol.REQUEST_REQUEST_ID);

        if (channel == null) {
            missingField(Protocol.PUBLISH_CHANNEL, Protocol.PUBLISH_FRAME);
            return;
        }
        if (event == null) {
            missingField(Protocol.PUBLISH_EVENT, Protocol.PUBLISH_FRAME);
            return;
        }
        if (requestID == null) {
            missingField(Protocol.REQUEST_REQUEST_ID, Protocol.PUBLISH_FRAME);
            return;
        }
        Log log = server.getLog(channel);
        if (log == null) {
            sendErrorResponse(Client.ERR_NO_SUCH_CHANNEL, "no such channel " + channel, requestID);
            return;
        }
        BsonObject record = new BsonObject();
        record.put(Protocol.RECEV_TIMESTAMP, System.currentTimeMillis());
        record.put(Protocol.RECEV_EVENT, event);
        CompletableFuture<Long> cf = log.append(record);

        cf.handle((v, ex) -> {
            if (ex == null) {
                BsonObject resp = new BsonObject();
                resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
                resp.put(Protocol.RESPONSE_OK, true);
                writeResponse(Protocol.RESPONSE_FRAME, resp);
            } else {
                sendErrorResponse(Client.ERR_SERVER_ERROR, "failed to persist", requestID);
            }

            return null;
        });
    }

    @Override
    public void handleStartTx(BsonObject frame) {
        checkContext();

        if (!isAuthorized(Protocol.STARTTX_FRAME)) {
            return;
        }
    }

    @Override
    public void handleCommitTx(BsonObject frame) {
        checkContext();

        if (!isAuthorized(Protocol.COMMITTX_FRAME)) {
            return;
        }
    }

    @Override
    public void handleAbortTx(BsonObject frame) {
        checkContext();

        if (!isAuthorized(Protocol.ABORTTX_FRAME)) {
            return;
        }
    }

    @Override
    public void handleSubscribe(BsonObject frame) {
        checkContext();

        if (!isAuthorized(Protocol.SUBSCRIBE_FRAME)) {
            return;
        }

        CompletableFuture<Boolean> authorisedCF = user.isAuthorised(Protocol.SUBSCRIBE_FRAME);

        //TODO: how to apply this correctly?
        //is this the right way?
        authorisedCF.handle((res, ex) -> {
            String channel = frame.getString(Protocol.SUBSCRIBE_CHANNEL);
            if (channel == null) {
                missingField(Protocol.SUBSCRIBE_CHANNEL, Protocol.SUBSCRIBE_FRAME);
                return null;
            }
            Integer requestID = frame.getInteger(Protocol.REQUEST_REQUEST_ID);
            if (requestID == null) {
                missingField(Protocol.REQUEST_REQUEST_ID, Protocol.SUBSCRIBE_FRAME);
                return null;
            }
            Long startSeq = frame.getLong(Protocol.SUBSCRIBE_STARTPOS);
            Long startTimestamp = frame.getLong(Protocol.SUBSCRIBE_STARTTIMESTAMP);
            String durableID = frame.getString(Protocol.SUBSCRIBE_DURABLEID);
            BsonObject matcher = frame.getBsonObject(Protocol.SUBSCRIBE_MATCHER);
            SubDescriptor subDescriptor = new SubDescriptor().setStartPos(startSeq == null ? -1 : startSeq).setStartTimestamp(startTimestamp)
                    .setMatcher(matcher).setDurableID(durableID).setChannel(channel);
            int subID = subSeq++;
            checkWrap(subSeq);
            Log log = server.getLog(channel);
            if (log == null) {
                sendErrorResponse(Client.ERR_NO_SUCH_CHANNEL, "no such channel " + channel, requestID);
                return null;
            }
            SubscriptionImpl subscription = new SubscriptionImpl(this, subID, subDescriptor);
            subscriptionMap.put(subID, subscription);
            BsonObject resp = new BsonObject();
            resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
            resp.put(Protocol.RESPONSE_OK, true);
            resp.put(Protocol.SUBRESPONSE_SUBID, subID);
            writeResponse(Protocol.SUBRESPONSE_FRAME, resp);
            logger.trace("Subscribed channel: {} startSeq {}", channel, startSeq);
            return null;
        });

    }

    @Override
    public void handleSubClose(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.SUBCLOSE_FRAME)) {
            return;
        }
        Integer subID = frame.getInteger(Protocol.SUBCLOSE_SUBID);
        if (subID == null) {
            missingField(Protocol.SUBCLOSE_SUBID, Protocol.SUBCLOSE_FRAME);
            return;
        }
        Integer requestID = frame.getInteger(Protocol.REQUEST_REQUEST_ID);
        if (requestID == null) {
            missingField(Protocol.REQUEST_REQUEST_ID, Protocol.SUBCLOSE_FRAME);
            return;
        }
        SubscriptionImpl subscription = subscriptionMap.remove(subID);
        if (subscription == null) {
            invalidField(Protocol.SUBCLOSE_SUBID, Protocol.SUBCLOSE_FRAME);
            return;
        }
        subscription.close();
        BsonObject resp = new BsonObject();
        resp.put(Protocol.RESPONSE_OK, true);
        resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
        writeResponse(Protocol.RESPONSE_FRAME, resp);
    }

    @Override
    public void handleUnsubscribe(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.UNSUBSCRIBE_FRAME)) {
            return;
        }

        Integer subID = frame.getInteger(Protocol.UNSUBSCRIBE_SUBID);
        if (subID == null) {
            missingField(Protocol.UNSUBSCRIBE_SUBID, Protocol.UNSUBSCRIBE_FRAME);
            return;
        }
        Integer requestID = frame.getInteger(Protocol.REQUEST_REQUEST_ID);
        if (requestID == null) {
            missingField(Protocol.REQUEST_REQUEST_ID, Protocol.UNSUBSCRIBE_FRAME);
            return;
        }
        SubscriptionImpl subscription = subscriptionMap.remove(subID);
        if (subscription == null) {
            invalidField(Protocol.UNSUBSCRIBE_SUBID, Protocol.UNSUBSCRIBE_FRAME);
            return;
        }
        subscription.close();
        subscription.unsubscribe();
        BsonObject resp = new BsonObject();
        resp.put(Protocol.RESPONSE_OK, true);
        resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
        writeResponse(Protocol.RESPONSE_FRAME, resp);
    }

    @Override
    public void handleAckEv(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.ACKEV_FRAME)) {
            return;
        }

        Integer subID = frame.getInteger(Protocol.ACKEV_SUBID);
        if (subID == null) {
            missingField(Protocol.ACKEV_SUBID, Protocol.ACKEV_FRAME);
            return;
        }
        Integer bytes = frame.getInteger(Protocol.ACKEV_BYTES);
        if (bytes == null) {
            missingField(Protocol.ACKEV_BYTES, Protocol.ACKEV_FRAME);
            return;
        }
        Long pos = frame.getLong(Protocol.ACKEV_POS);
        if (pos == null) {
            missingField(Protocol.ACKEV_POS, Protocol.ACKEV_FRAME);
            return;
        }
        SubscriptionImpl subscription = subscriptionMap.get(subID);
        if (subscription == null) {
            invalidField(Protocol.ACKEV_SUBID, Protocol.ACKEV_FRAME);
            return;
        }
        subscription.handleAckEv(pos, bytes);
    }

    @Override
    public void handleQuery(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.QUERY_FRAME)) {
            return;
        }
        Integer queryID = frame.getInteger(Protocol.QUERY_QUERYID);
        if (queryID == null) {
            missingField(Protocol.QUERY_QUERYID, Protocol.QUERY_FRAME);
            return;
        }
        String docID = frame.getString(Protocol.QUERY_DOCID);
        String binderName = frame.getString(Protocol.QUERY_BINDER);
        BsonObject matcher = frame.getBsonObject(Protocol.QUERY_MATCHER);
        Binder binder = server.getBinder(binderName);
        if (binder != null) {
            if (docID != null) {
                CompletableFuture<BsonObject> cf = binder.get(docID);
                cf.thenAccept(doc -> writeQueryResult(doc, queryID, true));
            } else if (matcher != null) {
                // TODO currently just use select all
                DocReadStream rs = binder.getMatching(doc -> true);
                QueryState holder = new QueryState(this, queryID, rs);
                rs.handler(holder);
                queryStates.put(queryID, holder);
                rs.start();
            }
        } else {
            writeQueryError(Client.ERR_NO_SUCH_BINDER, "No such binder " + binderName, queryID);
        }
    }

    @Override
    public void handleQueryAck(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.QUERYACK_FRAME)) {
            return;
        }

        Integer queryID = frame.getInteger(Protocol.QUERYACK_QUERYID);
        if (queryID == null) {
            missingField(Protocol.QUERYACK_QUERYID, Protocol.QUERYACK_FRAME);
            return;
        }
        Integer bytes = frame.getInteger(Protocol.QUERYACK_BYTES);
        if (bytes == null) {
            missingField(Protocol.QUERYACK_BYTES, Protocol.QUERYACK_FRAME);
            return;
        }
        QueryState queryState = queryStates.get(queryID);
        if (queryState != null) {
            queryState.handleAck(bytes);
        }
    }

    @Override
    public void handlePing(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.PING_FRAME)) {
            return;
        }
    }

    // Admin operations

    @Override
    public void handleListBinders(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.LIST_BINDERS_FRAME)) {
            return;
        }
        Integer requestID = frame.getInteger(Protocol.REQUEST_REQUEST_ID);
        if (requestID == null) {
            missingField(Protocol.REQUEST_REQUEST_ID, Protocol.LIST_BINDERS_FRAME);
            return;
        }
        BsonObject resp = new BsonObject();
        resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
        resp.put(Protocol.RESPONSE_OK, true);
        BsonArray arr = new BsonArray(server.listBinders());
        resp.put(Protocol.LISTBINDERS_BINDERS, arr);
        writeResponse(Protocol.RESPONSE_FRAME, resp);
    }

    @Override
    public void handleCreateBinder(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.CREATEBINDER_NAME)) {
            return;
        }
        Integer requestID = frame.getInteger(Protocol.REQUEST_REQUEST_ID);
        if (requestID == null) {
            missingField(Protocol.REQUEST_REQUEST_ID, Protocol.CREATE_BINDER_FRAME);
            return;
        }
        String binderName = frame.getString(Protocol.CREATEBINDER_NAME);
        if (binderName == null) {
            missingField(Protocol.CREATEBINDER_NAME, Protocol.CREATE_BINDER_FRAME);
            return;
        }
        CompletableFuture<Boolean> cf = server.createBinder(binderName);
        cf.handle((res, t) -> {
            if (t != null) {
                sendErrorResponse(Client.ERR_SERVER_ERROR, "failed to create binder", requestID);
            } else {
                BsonObject resp = new BsonObject();
                resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
                resp.put(Protocol.RESPONSE_OK, true);
                resp.put(Protocol.CREATEBINDER_RESPONSE_EXISTS, !res);
                writeResponse(Protocol.RESPONSE_FRAME, resp);
            }
            return null;
        });
    }

    @Override
    public void handleListChannels(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.LIST_CHANNELS_FRAME)) {
            return;
        }
        Integer requestID = frame.getInteger(Protocol.REQUEST_REQUEST_ID);
        if (requestID == null) {
            missingField(Protocol.REQUEST_REQUEST_ID, Protocol.LIST_CHANNELS_FRAME);
            return;
        }
        BsonObject resp = new BsonObject();
        resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
        resp.put(Protocol.RESPONSE_OK, true);
        BsonArray arr = new BsonArray(server.listChannels());
        resp.put(Protocol.LISTCHANNELS_CHANNELS, arr);
        writeResponse(Protocol.RESPONSE_FRAME, resp);
    }

    @Override
    public void handleCreateChannel(BsonObject frame) {
        checkContext();
        if (!isAuthorized(Protocol.CREATECHANNEL_NAME)) {
            return;
        }
        Integer requestID = frame.getInteger(Protocol.REQUEST_REQUEST_ID);
        if (requestID == null) {
            missingField(Protocol.REQUEST_REQUEST_ID, Protocol.CREATE_CHANNEL_FRAME);
            return;
        }
        String channelName = frame.getString(Protocol.CREATECHANNEL_NAME);
        if (channelName == null) {
            missingField(Protocol.CREATECHANNEL_NAME, Protocol.CREATE_CHANNEL_FRAME);
            return;
        }
        CompletableFuture<Boolean> cf = server.createChannel(channelName);
        cf.handle((res, t) -> {
            if (t != null) {
                sendErrorResponse(Client.ERR_SERVER_ERROR, "failed to create channel", requestID);
            } else {
                BsonObject resp = new BsonObject();
                resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
                resp.put(Protocol.RESPONSE_OK, true);
                resp.put(Protocol.CREATECHANNEL_RESPONSE_EXISTS, !res);
                writeResponse(Protocol.RESPONSE_FRAME, resp);
            }
            return null;
        });
    }

    protected Buffer writeQueryResult(BsonObject doc, int queryID, boolean last) {
        BsonObject res = new BsonObject();
        res.put(Protocol.QUERYRESULT_OK, true);
        res.put(Protocol.QUERYRESULT_QUERYID, queryID);
        res.put(Protocol.QUERYRESULT_RESULT, doc);
        res.put(Protocol.QUERYRESULT_LAST, last);
        return writeResponse(Protocol.QUERYRESULT_FRAME, res);
    }

    protected Buffer writeQueryError(int errCode, String errMsg, int queryID) {
        BsonObject res = new BsonObject();
        res.put(Protocol.QUERYRESULT_OK, false);
        res.put(Protocol.QUERYRESULT_QUERYID, queryID);
        res.put(Protocol.QUERYRESULT_LAST, true);
        res.put(Protocol.RESPONSE_ERRCODE, errCode);
        res.put(Protocol.RESPONSE_ERRMSG, errMsg);
        return writeResponse(Protocol.QUERYRESULT_FRAME, res);
    }

    protected Buffer writeResponse(String frameName, BsonObject frame) {
        Buffer buff = Protocol.encodeFrame(frameName, frame);
        // TODO compare performance of writing directly in all cases and via context
        Context curr = Vertx.currentContext();
        if (curr != context) {
            context.runOnContext(v -> transportConnection.write(buff));
        } else {
            transportConnection.write(buff);
        }
        return buff;
    }

    protected void checkWrap(int i) {
        // Sanity check - wrap around - won't happen but better to close connection than give incorrect behaviour
        if (i == Integer.MIN_VALUE) {
            String msg = "int wrapped!";
            logger.error(msg);
            close();
        }
    }

//    protected boolean isAuthorised(String frameName) {
//        CompletableFuture<Boolean> authorised = user.isAuthorised(frameName);
//
//        authorised.handle((res, ex) -> {
//
//        });
//    }

    protected boolean isAuthorized(String frameName) {
//        if (!user.isAuthorized(frameName)) {
//            BsonObject resp = new BsonObject();
//            resp.put(Protocol.RESPONSE_OK, false);
//            resp.put(Protocol.RESPONSE_ERRMSG, "User is not authorized to perform the operation!");
//            writeResponse(Protocol.RESPONSE_FRAME, resp);
//            logAndClose("Not authorized");
//        }
//        return user.isAuthorized(frameName);

        //dummy result for now until auth is done properly
        return true;
    }

    protected void missingField(String fieldName, String frameType) {
        logger.warn("protocol error: missing {} in {}. connection will be closed", fieldName, frameType);
        close();
    }

    protected void invalidField(String fieldName, String frameType) {
        logger.warn("protocol error: invalid {} in {}. connection will be closed", fieldName, frameType);
        close();
    }

    protected void logAndClose(String exceptionMessage) {
        logger.error("{}, Connection will be closed", exceptionMessage);
        close();
    }

    protected void sendErrorResponse(int errCode, String errMsg) {
        sendErrorResponse(errCode, errMsg, null);
    }

    protected void sendErrorResponse(int errCode, String errMsg, Integer requestID) {
        BsonObject resp = new BsonObject();
        if (requestID != null) {
            resp.put(Protocol.RESPONSE_REQUEST_ID, requestID);
        }
        resp.put(Protocol.RESPONSE_OK, false);
        resp.put(Protocol.RESPONSE_ERRCODE, errCode);
        resp.put(Protocol.RESPONSE_ERRMSG, errMsg);
        writeResponse(Protocol.RESPONSE_FRAME, resp);
    }

    // Sanity check - this should always be executed using the correct context
    protected void checkContext() {
        if (Vertx.currentContext() != context) {
            logger.trace("Wrong context!! " + Thread.currentThread() + " expected " + context, new Exception());
            throw new IllegalStateException("Wrong context!");
        }
    }

    protected void removeQueryState(int queryID) {
        checkContext();
        queryStates.remove(queryID);
    }

    protected void close() {
        checkContext();
        if (closed) {
            return;
        }

        //authenticated = false;
        user = new UnauthorizedUser();

        for (QueryState queryState : queryStates.values()) {
            queryState.close();
        }
        closed = true;
        transportConnection.close();
    }

    protected ServerImpl server() {
        return server;
    }

}
