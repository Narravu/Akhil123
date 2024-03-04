import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;

@SlingServlet(paths = "/bin/main/path")
@Component(service = Servlet.class,immediate = true)
public class MainServlet extends SlingAllMethodsServlet {

    private final String TALENND_API = "https://dev.agrohub.basf.com/basf-eupf-eapi/api/services/v1/";
    private final String client_id = "1a3fdb92-8188-4021-8dfd-05776d260890";
    private final String client_secret = "jFfbaor_0ocQbWTL8PpnMLHNOyeMz-yxhyypO19Y-2W8cdOsCV-UYKBMe2TjXCyaTunZ_tNryyKfZSIo_t6CRA";

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        PrintWriter out = response.getWriter();
        response.setContentType("application/json");
        out.print(talendPost(TALENND_API,getRequestJsonObject(request),"contact/alphabet"));

    }

    protected JsonObject getRequestJsonObject(SlingHttpServletRequest request) throws IOException {
        // Read the JSON string from the request body
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        String jsonString = sb.toString();

        // Parse the JSON string using Gson
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(jsonString, JsonObject.class);
        return jsonObject;
    }

    protected JsonObject getHeaderJsonObject(SlingHttpServletRequest request) throws IOException {
        // Read the JSON string from the request body
        Enumeration<String> headers = request.getHeaderNames();
        JsonObject headerJson = new JsonObject();
        while (headers.hasMoreElements()) {
            String headerName = headers.nextElement();
            String headervalue = request.getHeader(headerName);
            headerJson.addProperty(headerName, headervalue);
        }
        return headerJson;
    }

    // PUT THE BELOW METHOD IN SERVICE LAYER
    public JsonObject talendPost(String url, JsonObject jsonBody,String endpoint)
    {
        String result = null;
        HttpPost httpPost = new HttpPost(TALENND_API+endpoint);
        httpPost.setEntity(new StringEntity(jsonBody.toString(), ContentType.APPLICATION_JSON));
        httpPost.addHeader("client_id", client_id);
        httpPost.addHeader("client_secret", client_secret);
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = httpclient.execute(httpPost)) {

                result = EntityUtils.toString(response.getEntity());
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
            return null;
        }
        return (JsonObject) new JsonParser().parse(result);
    }

}
