package de.fhg.iais.roberta.javaServer.restServices.all;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.google.inject.Inject;

import de.fhg.iais.roberta.javaServer.provider.OraData;
import de.fhg.iais.roberta.persistence.ConfigurationProcessor;
import de.fhg.iais.roberta.persistence.UserProcessor;
import de.fhg.iais.roberta.persistence.bo.Configuration;
import de.fhg.iais.roberta.persistence.bo.User;
import de.fhg.iais.roberta.persistence.util.DbSession;
import de.fhg.iais.roberta.persistence.util.HttpSessionState;
import de.fhg.iais.roberta.robotCommunication.RobotCommunicator;
import de.fhg.iais.roberta.util.AliveData;
import de.fhg.iais.roberta.util.ClientLogger;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.Util;
import de.fhg.iais.roberta.util.Util1;

@Path("/conf")
public class ClientConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(ClientConfiguration.class);

    private final RobotCommunicator brickCommunicator;

    @Inject
    public ClientConfiguration(RobotCommunicator brickCommunicator) {
        this.brickCommunicator = brickCommunicator;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response command(@OraData HttpSessionState httpSessionState, @OraData DbSession dbSession, JSONObject fullRequest) throws Exception {
        AliveData.rememberClientCall();
        MDC.put("sessionId", String.valueOf(httpSessionState.getSessionNumber()));
        MDC.put("userId", String.valueOf(httpSessionState.getUserId()));
        MDC.put("robotName", String.valueOf(httpSessionState.getRobotName()));
        new ClientLogger().log(ClientConfiguration.LOG, fullRequest);
        int userId = httpSessionState.getUserId();
        final String robotName =
            httpSessionState.getRobotFactory(httpSessionState.getRobotName()).getGroup() != ""
                ? httpSessionState.getRobotFactory(httpSessionState.getRobotName()).getGroup()
                : httpSessionState.getRobotName();
        UserProcessor up = new UserProcessor(dbSession, httpSessionState);
        JSONObject response = new JSONObject();
        try {
            JSONObject request = fullRequest.getJSONObject("data");
            String cmd = request.getString("cmd");
            ClientConfiguration.LOG.info("command is: " + cmd);
            response.put("cmd", cmd);
            ConfigurationProcessor cp = new ConfigurationProcessor(dbSession, httpSessionState);
            if ( cmd.equals("saveC") ) {
                String configurationName = request.getString("name");
                String configurationXml = request.getString("configuration");
                cp.updateConfiguration(configurationName, userId, robotName, configurationXml, true);
                Util.addResultInfo(response, cp);

            } else if ( cmd.equals("saveAsC") ) {
                String configurationName = request.getString("name");
                String configurationXml = request.getString("configuration");
                cp.updateConfiguration(configurationName, userId, robotName, configurationXml, false);
                Util.addResultInfo(response, cp);

            } else if ( cmd.equals("loadC") ) {
                String configurationName = request.getString("name");
                String ownerName = request.getString("owner").trim();
                if ( !ownerName.isEmpty() ) {
                    User user = up.getUser(ownerName);
                    if ( user != null ) {
                        userId = user.getId();
                    }
                }
                Configuration configuration = cp.getConfiguration(configurationName, userId, robotName);
                if ( configuration != null ) {
                    response.put("data", configuration.getConfigurationText());
                }
                Util.addResultInfo(response, cp);

            } else if ( cmd.equals("deleteC") && httpSessionState.isUserLoggedIn() ) {
                String configurationName = request.getString("name");
                cp.deleteByName(configurationName, userId, robotName);
                Util.addResultInfo(response, cp);

            } else if ( cmd.equals("loadCN") && httpSessionState.isUserLoggedIn() ) {
                JSONArray configurationInfo = cp.getConfigurationInfo(userId, robotName);
                response.put("configurationNames", configurationInfo);
                Util.addResultInfo(response, cp);

            } else {
                ClientConfiguration.LOG.error("Invalid command: " + cmd);
                Util.addErrorInfo(response, Key.COMMAND_INVALID);
            }
            dbSession.commit();
        } catch ( Exception e ) {
            dbSession.rollback();
            String errorTicketId = Util1.getErrorTicketId();
            ClientConfiguration.LOG.error("Exception. Error ticket: " + errorTicketId, e);
            Util.addErrorInfo(response, Key.SERVER_ERROR).append("parameters", errorTicketId);
        } finally {
            if ( dbSession != null ) {
                dbSession.close();
            }
        }
        Util.addFrontendInfo(response, httpSessionState, this.brickCommunicator);
        MDC.clear();
        return Response.ok(response).build();
    }
}