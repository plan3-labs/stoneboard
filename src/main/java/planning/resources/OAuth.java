package planning.resources;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.eclipse.jetty.util.ConcurrentHashSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jersey.api.client.Client;

@Path("/auth")
@Produces(APPLICATION_JSON)
public class OAuth {

    // Doesn't scale beyond 1 dyno
    @Deprecated
    private static final Set<String> states = new ConcurrentHashSet<>();
    private final String clientId;
    private final String clientSecret;

    public OAuth(final String clientId, final String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @GET
    public Response login() {
        final URI login = UriBuilder
                .fromUri(URI.create("https://github.com/login/oauth/authorize"))
                .queryParam("client_id", this.clientId)
                .queryParam("scope", "repo,read:org")
                .queryParam("state", newState())
                .build();
        return Response.temporaryRedirect(login).build();
    }

    @GET
    @Path("callback")
    public Response callback(@QueryParam("code") final String code,
                             @QueryParam("state") final String state,
                             @Context final HttpServletRequest request) {
        assertState(state);
        final JsonNode root = Client.create()
                .resource("https://github.com/login/oauth/access_token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("code", code)
                .queryParam("client_id", this.clientId)
                .queryParam("client_secret", this.clientSecret)
                .accept(APPLICATION_JSON)
                .post(JsonNode.class);
        request.getSession().setAttribute("token", checkNotNull(emptyToNull(root.get("access_token").asText())));
        return Response.temporaryRedirect(UriBuilder.fromResource(Milestones.class).build()).build();
    }

    private static String newState() {
        final String state = UUID.randomUUID().toString();
        states.add(state);
        return state;
    }

    private static void assertState(final String state) {
        if(!states.remove(state)) {
            throw new WebApplicationException(BAD_REQUEST);
        }
    }
}