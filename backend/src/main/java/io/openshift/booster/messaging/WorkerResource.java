package io.openshift.booster.messaging;

import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkerResource {

    private static final String ID = "worker-quarkus-" + UUID.randomUUID()
                                        .toString().substring(0, 4);

    @ConfigProperty(name = "worker.cloud.id", defaultValue = "notset")
    String cloudId;

    @ConfigProperty(name = "KUBERNETES_NAMESPACE", defaultValue = "myspace?")
    String namespace;

    private static String greeting = "Aloha";

    int cnt = 0;

    @GET
    @Path("/alive")
    @Produces(MediaType.TEXT_PLAIN)
    public javax.ws.rs.core.Response alive() {
        String message = "alive";

        return javax.ws.rs.core.Response
          .status(javax.ws.rs.core.Response.Status.OK)
          .entity(message)
          .build();
    }

    @GET
    @Path("/ready")
    @Produces(MediaType.TEXT_PLAIN)
    public javax.ws.rs.core.Response ready() {
        String message = "ready";

        return javax.ws.rs.core.Response
          .status(javax.ws.rs.core.Response.Status.OK)
          .entity(message)
          .build();
    }

    @GET
    @Path("/cloud")
    @Produces(MediaType.TEXT_PLAIN)
    public javax.ws.rs.core.Response cloud() {
        return javax.ws.rs.core.Response
          .status(javax.ws.rs.core.Response.Status.OK)
          .entity(cloudId + ":" + cnt++)
          .build();
    }



    @POST
    @Path("/process")
    public Response process(Message message) {
        System.out.println(message);
        Response response = new Response(message.getRequestId(), ID, cloudId, process(message.getRequest()));
        return response;
    }

    private String process(Request request) {
    
        String text = request.getText();

        if (request.isUppercase()) {
            text = text.toUpperCase();
        }
    
        if (request.isReverse()) {
            text = new StringBuilder(text).reverse().toString();
        }
        return greeting + " " + text;
    }
}