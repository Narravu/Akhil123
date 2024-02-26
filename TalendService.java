package com.basfeupf.core.services.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map.Entry;
import java.util.Objects;

import com.basfeupf.core.utils.PayLoad;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.osgi.framework.ServiceException;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basfeupf.core.constants.Basf_Constant;
import com.basfeupf.core.services.AppAccessTokenService;
import com.basfeupf.core.services.AuthConfigService;
import com.basfeupf.core.services.AzureAuthService;
import com.basfeupf.core.services.EupfService;
import com.basfeupf.core.services.LogServise;
import com.basfeupf.core.services.TalendServise;
import com.basfeupf.core.services.UserDetailsService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.servlet.ServletException;

@Component(service = TalendServise.class)
public class TalendServiseImpl implements TalendServise {

	@Reference
	AuthConfigService authConfigService;

	@Reference
	AzureAuthService azureAuthService;

	@Reference
	EupfService eupfService;

	@Reference
	AppAccessTokenService appAccessTokenService;

	@Reference
	UserDetailsService userDetailsService;

	@Reference
	private HttpClientBuilderFactory httpClientBuilderFactory;

	@Reference
	LogServise logServise;

	Logger logger = LoggerFactory.getLogger(this.getClass());


	@Override
	public JsonObject getTalendRequestJson(String requestAttribute, String requestType, String accountType,
										   String businessSegment, String sub, String account_id, String contactId, JsonArray attrJsonArray)
			throws Exception {

		JsonObject talendRequestJson = new JsonObject();
		talendRequestJson.addProperty("EUPF_ID", sub);
		talendRequestJson.addProperty("Request_Attribute", requestAttribute);
		talendRequestJson.addProperty("Request_Type", requestType);
		talendRequestJson.addProperty("Account_Type", accountType);
		talendRequestJson.addProperty("Business_Segment", businessSegment);
		talendRequestJson.addProperty("Account_BASF_ID", account_id);
		talendRequestJson.addProperty("ContactId", contactId);
		talendRequestJson.add("Attributes", attrJsonArray);

		return talendRequestJson;
	}

	@Override
	public void add_attr_value_in_response(JsonObject talendResponseJson, JsonArray app_data_array,
										   JsonObject responseJson) throws Exception {

		// for add attr_value in response
		boolean allFieldsSubmitted = false;
		int fieldsSubmittedSize = 0;

		if (talendResponseJson.has("Attributes")) {
			JsonArray talendResponseAttrArray = talendResponseJson.get("Attributes").getAsJsonArray();

			for (JsonElement attributesElement : talendResponseAttrArray) {
				JsonObject attributesJson = attributesElement.getAsJsonObject();
//				String attr_key = attributesJson.has("attr_key") ? attributesJson.get("attr_key").getAsString() : "";
//				String attr_value = attributesJson.has("attr_value") ? attributesJson.get("attr_value").getAsString()
//						: "";

				String attr_key = attributesJson.has("attr_key") ? getValue(attributesJson,"attr_key") : "";
				String attr_value = attributesJson.has("attr_value") ? getValue(attributesJson ,"attr_value") : "";

				for (JsonElement jsonElement : app_data_array) {
					JsonObject jsonObject = jsonElement.getAsJsonObject();

					if (jsonObject.has("attrib_map_id")) {
						String attrib_map_id = jsonObject.get("attrib_map_id").getAsString();

						if (attrib_map_id.equals(attr_key)) {
							String editable = jsonObject.has("editable") ? jsonObject.get("editable").getAsString()
									: "";
							jsonObject.addProperty("attrib_value", attr_value);
							jsonObject.addProperty("editable", editable);

							if(!attr_value.equalsIgnoreCase("")) {
								fieldsSubmittedSize++;
							}
							break;
						}
					}
				}
			}
		}
		if (fieldsSubmittedSize > 0 && app_data_array.size() == fieldsSubmittedSize) {
			allFieldsSubmitted = true;
		}
		responseJson.addProperty("allFieldsSubmitted", allFieldsSubmitted);
	}

	@Override
	public JsonObject talendAPI(String jwt_token, String state, String xApiKey, String xApiClientId, String url,JsonObject userDetilsjson) throws Exception {
		JsonObject payloadJson = azureAuthService.getPayloadJson(jwt_token);
		JsonObject headerJson = new JsonObject();
		JsonObject responseJson=new JsonObject();
		//String xApiKey = "0ad66f9f-1834-4a42-84db-3b5357c8dbea";// config
//		headerJson.addProperty("x-api-key", xApiKey);
//		headerJson.addProperty("x-id-token", jwt_token);
		headerJson.addProperty("client_secret", xApiKey);
		headerJson.addProperty("client_id", xApiClientId);

		JsonObject requestJson = getRequest(jwt_token, state, userDetilsjson, payloadJson);
		logger.debug("talend requestJson"+requestJson);
		//String url = authConfigService.getTalendEndpointUrl();

//		if (state.equalsIgnoreCase(Basf_Constant.PARTNER)) {
//			/// comment this when we have actual data for partner case
//			url = "https://run.mocky.io/v3/c8f89acf-06af-4103-aa11-cfe495e80f8f";
//		}

		responseJson = callPost(requestJson, url, headerJson);
		logger.debug("Talend Response: " + responseJson);
		if(responseJson.has("talendjson")){
			responseJson.get("talendjson").getAsJsonObject().get("Profile_Data").getAsJsonObject().addProperty("business_segment", payloadJson.get("extension_user_Registration_bs").getAsString());
		}
		logger.debug("Talend Response:"+responseJson);
		return responseJson;
	}


	@Override
	public JsonObject talendAPIGeneric(String xApiClientId,  String xApiKey, String url, JsonObject requestJson ) throws Exception {

		JsonObject headerJson = new JsonObject();
//		headerJson.addProperty("x-api-key", xApiKey);
//		headerJson.addProperty("x-id-token", jwt_token);
		headerJson.addProperty("client_secret", xApiKey);
		headerJson.addProperty("client_id", xApiClientId);

		//JsonObject requestJson = getRequest(jwt_token, state);
		//String url = authConfigService.getTalendEndpointUrl();

//		if (state.equalsIgnoreCase(Basf_Constant.PARTNER)) {
//			/// comment this when we have actual data for partner case
//			url = "https://run.mocky.io/v3/c8f89acf-06af-4103-aa11-cfe495e80f8f";
//		}
		JsonObject responseJson = callPost(requestJson, url, headerJson);

		return responseJson;
	}

	@Override
	public JsonObject callGet(String url, JsonObject headerJson)
			throws ClientProtocolException, IOException, ServiceException {

		long lStartTime = System.nanoTime();
		//HttpClient client = HttpClientBuilder.create().build();
		HttpClientBuilder builder = httpClientBuilderFactory.newBuilder();
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectTimeout(authConfigService.getApigee_timeout())
				.setSocketTimeout(authConfigService.getApigee_timeout()).build();
		builder.setDefaultRequestConfig(requestConfig);
		HttpClient client = builder.build();
		HttpGet get = new HttpGet(url);
		// Add Headers Dynamically
		if (Objects.nonNull(headerJson)) {
			for (Entry<String, JsonElement> entry : headerJson.entrySet()) {
				get.addHeader(entry.getKey(), entry.getValue().getAsString());
			}
		}

		HttpResponse httpResponse = client.execute(get);



		int statusCode = httpResponse.getStatusLine().getStatusCode();

		if (statusCode != 200 && statusCode != 201) {
			if (httpResponse.getStatusLine().getStatusCode() == 401) {
				throw new ServiceException("Authorization Failed");
			} else {
				throw new ServiceException(" Failed HTTP Resonse: " + httpResponse.toString());
			}
		}

		BufferedReader br = new BufferedReader(new InputStreamReader((httpResponse.getEntity().getContent())));
		Gson gson = new Gson();
		JsonObject updatedResponse = gson.fromJson(br, JsonObject.class);

		long lEndTime = System.nanoTime();
		logServise.log_message(url,headerJson.toString(), updatedResponse.toString(), String.valueOf((lEndTime - lStartTime)/1000));
		return updatedResponse;
	}

	@Override
	public JsonObject callPost(JsonObject requestBody, String url, JsonObject headerJson)
			throws ServiceException, IOException {
		//HttpClient client = HttpClientBuilder.create().build();
		HttpClientBuilder builder = httpClientBuilderFactory.newBuilder();
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectTimeout(authConfigService.getApigee_timeout())
				.setSocketTimeout(authConfigService.getApigee_timeout()).build();
		builder.setDefaultRequestConfig(requestConfig);
		HttpClient client = builder.build();
		HttpPost post = new HttpPost(url);
		post.addHeader("Content-Type", "application/json");

		// Add Headers Dynamically
		if (Objects.nonNull(headerJson)) {
			for (Entry<String, JsonElement> entry : headerJson.entrySet()) {
				post.addHeader(entry.getKey(), entry.getValue().getAsString());
			}
		}

		long lStartTime = System.nanoTime();

		post.setEntity(new StringEntity(requestBody.toString()));
		HttpResponse httpResponse = client.execute(post);

		int statusCode = httpResponse.getStatusLine().getStatusCode();
		JsonObject updatedResponse = new JsonObject();

		if (statusCode != 200 && statusCode != 201) {
			if (httpResponse.getStatusLine().getStatusCode() == 401) {
				//	throw new ServiceException("Authorization Failed");
			} else {
				//throw new ServiceException(" Failed HTTP Resonse: " + httpResponse.toString());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader((httpResponse.getEntity().getContent())));
			Gson gson = new Gson();
			updatedResponse = gson.fromJson(br, JsonObject.class);
			logger.debug("requestBody"+requestBody.toString()+"error "+statusCode+"response"+updatedResponse.toString());
			updatedResponse=null;
		} else {
			BufferedReader br = new BufferedReader(new InputStreamReader((httpResponse.getEntity().getContent())));
			Gson gson = new Gson();
			updatedResponse = gson.fromJson(br, JsonObject.class);

			long lEndTime = System.nanoTime();
			logServise.log_message(url,requestBody.toString(), updatedResponse.toString(), String.valueOf((lEndTime - lStartTime)/1000));
		}


		return updatedResponse;
	}

	@Override
	public String getValue(JsonObject jsonObject, String key) throws Exception {
		try {
			return jsonObject.get(key).getAsString();
		}catch (Exception n){
			return "";
		}
	}

	@Override
	public String getMdmValue(String[] array, String key){
		for (String str : array){
			if (!str.equals("")){
				if (str.split(":")[0].equalsIgnoreCase(key)){
					return str.split(":")[1];
				}
			}
		}
		return "";
	}

	@Override
	public void doProcess(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
			// URL for the POST request
			String client_secret = "";
			String client_id = "";
			client_secret=authConfigService.getEupf_apigee_talend_key();
			client_id = authConfigService.getEupf_apigee_talend_client_id();
		    String endPoint=request.getParameter("page");
			StringBuilder jsonBody = new StringBuilder();
			BufferedReader br = new BufferedReader(new InputStreamReader(request.getInputStream()));
			String line;
			while ((line = br.readLine()) != null) {
				jsonBody.append(line);
			}
			br.close();

			Gson gson=new Gson();
		    PayLoad p=gson.fromJson(jsonBody.toString(),PayLoad.class);

			// Set up the connection for the POST request
			URL url = new URL(authConfigService.getTalendEndpointUrlv1()+p.getPage());
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("client_id", client_id);
			connection.setRequestProperty("client_secret", client_secret);
			connection.setDoOutput(true);

			// Send the POST request
			PrintWriter out = new PrintWriter(connection.getOutputStream());
			out.println(jsonBody.toString());
			out.close();
			// Read the response from the POST request
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			StringBuilder responseBuilder = new StringBuilder();
			// String line;
			while ((line = in.readLine()) != null) {
				responseBuilder.append(line);
			}
			in.close();
			String jsonResponse = responseBuilder.toString();
			// Set the response content type
			response.setContentType("application/json");

			// Send the response to the client
			PrintWriter writer = response.getWriter();
			writer.print(jsonResponse);
			writer.flush();
		}
	@Override
	public JsonObject getRequest(String jwt_token, String state, JsonObject userDetilsjson, JsonObject payloadJson) throws Exception {

		JsonObject jsonObject = new JsonObject();

//		JsonObject payloadJson = azureAuthService.getPayloadJson(jwt_token);
		String accountType[] = authConfigService.getAccounttype();
		String mdmBusinessSegment[] = authConfigService.getBusinessSegment();
//		JsonObject payloadJson = azureAuthService.getPayloadJson("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJSZXF1ZXN0X1R5cGUiOiJSZWdpc3RyYXRpb24iLCJSZXF1ZXN0X0F0dHJpYnV0ZSI6IlByb2ZpbGUiLCJFVVBGX0lEIjoiNGQxNzNlZjYtMGI1NS00MTQ2LWE4ZDctMGZjYTVjNjE0ZjU1IiwiRW1haWxfQWRkcmVzcyI6InNhbXBsMWVAbWRtLmNvbSIsIkNvbnRhY3RfRmlyc3RfTmFtZSI6IlRlc3Q1IiwiQ29udGFjdF9MYXN0X05hbWUiOiJNZG01IiwiTWFpbGluZ19BZGRyZXNzIjoiNTM3NSBTb3V0aCAzcmQgU3RyZWV0IHRlc3Q1IiwiQ2l0eSI6Ik1pbHdhdWtlZSB0ZXN0NSIsIlN0YXRlIjoiV0kgdGVzdDUiLCJaaXBjb2RlIjoiNTMyMDciLCJDb3VudHJ5IjoiVVMiLCJQaG9uZV9OdW1iZXIiOiIxMjM0NTY3ODkyIiwiQnVzaW5lc3NfTmFtZSI6IlRhcmdldHRlc3Q1IiwiQnVzaW5lc3NfU2VnbWVudCI6IkFQTjEwMDAxVVNBMjY4MjY1IiwiQWNjb3VudF9UeXBlIjoiQVBOMTAwMDFVU0EyNjEyNjEwMjI2In0.WAzP8-GmHG0_4Ye4NKos5UrFbqHyiriTuJmR2VIaqw4");

		String sub = getValue(payloadJson, "sub");
		String email = getValue(payloadJson, "email");
		String firstname = getValue(payloadJson, "firstname");
		String lastname = getValue(payloadJson, "lastname");
		String mailing_address = getValue(payloadJson, "extension_mailing_address");
		String mailing_city = getValue(payloadJson, "extension_mailing_city");
		String mailing_state = getValue(payloadJson, "extension_mailing_state");
		String mailing_postalCode = getValue(payloadJson, "extension_mailing_postalCode");
		String country = getValue(payloadJson, "country");
		String telephoneNumber = getValue(payloadJson, "telephoneNumber");
		String business_name = getValue(payloadJson, "extension_business_name");
		String contact_id = getValue(payloadJson, "extension_contact_id");
		String account_id = getValue(payloadJson, "extension_account_number");
		String lead_id = getValue(payloadJson, "prospect_id");
		String emailOpt = getValue(payloadJson,"extension_email_communication_consent").equalsIgnoreCase("true") ? "Yes" : "No";
		String prefLang = getValue(payloadJson, "preferredLanguage").equals("") ? "en-US" : getValue(payloadJson, "preferredLanguage") ;
		String accSubType = getValue(payloadJson, "businessType");
		String businessOrg = getValue(payloadJson, "extension_business_organization");

		String business_segment = getMdmValue(mdmBusinessSegment, getValue(payloadJson, "extension_user_Registration_bs"));
		String user_type = getMdmValue(accountType, getValue(payloadJson, "businessType"));

		JsonObject userRequestJson = new JsonObject();
		userRequestJson.addProperty("sub", sub);
		userRequestJson.addProperty("type", "get_user_id_sub");
		logger.debug("userDetilsjson type"+userDetilsjson.get("type").getAsString());
		if(userDetilsjson.has("type")) {
			if (userDetilsjson.get("type").getAsString().toLowerCase().equalsIgnoreCase("insert") && (state.equalsIgnoreCase(Basf_Constant.LOGIN) || state.equalsIgnoreCase(Basf_Constant.FORGOT_PASSWORD))) {
				state = Basf_Constant.REGISTER;// for user who are available in azure but not in crm
				if (business_segment.equalsIgnoreCase("")) {
					business_segment = "Pest Control Solutions";
				}
				if (user_type.equalsIgnoreCase("")) {
					user_type = "End User";
				}
			}
		}

			/*if(userDetilsjson.get("type").getAsString().toLowerCase().equalsIgnoreCase("update") && (state.equalsIgnoreCase(Basf_Constant.LOGIN) || state.equalsIgnoreCase(Basf_Constant.FORGOT_PASSWORD))) {
				JsonObject userIdJson = eupfService.callAwsRestService(userRequestJson);
				if (userIdJson.has(Basf_Constant.STATUS) && userIdJson.get(Basf_Constant.STATUS).getAsString().equals(Basf_Constant.STATUS_SUCCESS)) {
					if (userIdJson.has(Basf_Constant.DATA)) {
						JsonArray userIdArray = userIdJson.get(Basf_Constant.DATA).getAsJsonArray();
						for (JsonElement userElement : userIdArray) {
							JsonObject userJson = userElement.getAsJsonObject();
							if (userJson.has("contactid")) {
								contact_id=userJson.get("contactid").getAsString();

							}
							if (userJson.has("account_id")) {
								account_id=userJson.get("account_id").getAsString();
							}

							if (userJson.has("requested_business_segment")) {
								business_segment=userJson.get("requested_business_segment").getAsString();
							}
							if(userJson.has("lead_id")){
								lead_id = userJson.get("lead_id").getAsString();
							}
						}

						/********************return registration response********************************/

				/*		if((contact_id==null || contact_id.equalsIgnoreCase("")) || (account_id ==null || account_id.equalsIgnoreCase("")) ) {
							JsonObject contactJson = new JsonObject();
							JsonObject rootJson = new JsonObject();
							JsonObject profileJson = new JsonObject();
							profileJson.addProperty("Status", "Acknowledged");
							profileJson.addProperty("EUPF_ID", sub);
							profileJson.addProperty("Email_Address", email);
							profileJson.addProperty("business_segment", business_segment);
							profileJson.addProperty("Lead_ID", lead_id);
							contactJson.add("ContactData",profileJson);
							rootJson.add("root",contactJson);
							return rootJson;
							}

					}
				}
			}
		}*/

		if(state.equalsIgnoreCase(Basf_Constant.FORGOT_PASSWORD) && !account_id.equalsIgnoreCase("") &&  !contact_id.equalsIgnoreCase("")) {
			state=Basf_Constant.LOGIN;
		}

//		if(state.equalsIgnoreCase(Basf_Constant.FORGOT_PASSWORD)) {
//
//
//			JsonObject userIdJson = eupfService.callAwsRestService(userRequestJson);
//			boolean islogn=false;
//
//			if (userIdJson.has(Basf_Constant.STATUS)
//					&& userIdJson.get(Basf_Constant.STATUS).getAsString().equals(Basf_Constant.STATUS_SUCCESS)) {
//				if (userIdJson.has(Basf_Constant.DATA)) {
//					JsonArray userIdArray = userIdJson.get(Basf_Constant.DATA).getAsJsonArray();
//					for (JsonElement userElement : userIdArray) {
//						JsonObject userJson = userElement.getAsJsonObject();
//						if (userJson.has("contactid")) {
//
//							islogn=true;
//						}
//						if (userJson.has("account_id")) {
//						islogn=true;
//						}
//						break;
//					}
//				}
//			}
//
//			if(islogn) {
//				state=Basf_Constant.LOGIN;
//			} else {
//				state=Basf_Constant.REGISTER;
//			}
//
//		}
		logger.debug("state"+state);
		if (Objects.nonNull(state) && !state.isEmpty()) {
			logger.debug("state"+state);
			if (state.equalsIgnoreCase(Basf_Constant.REGISTER)  || state.equalsIgnoreCase(Basf_Constant.FORGOT_PASSWORD)) {
				jsonObject.addProperty("Request_Type", "Registration");
				jsonObject.addProperty("Request_Attribute", "Profile");//
				jsonObject.addProperty("EUPF_ID", sub);
				// email = "Dteliafaro@Ifa-Coop.Com";
				jsonObject.addProperty("Email_Address", email);
				jsonObject.addProperty("Contact_First_Name", firstname);
				jsonObject.addProperty("Contact_Last_Name", lastname);
				jsonObject.addProperty("Mailing_Address", mailing_address);
				jsonObject.addProperty("City", mailing_city);
				jsonObject.addProperty("State", mailing_state);
				jsonObject.addProperty("Zipcode", mailing_postalCode);
				jsonObject.addProperty("Country", country);
				jsonObject.addProperty("Mobile_Phone", telephoneNumber);
				jsonObject.addProperty("Business_Name", business_name);
				jsonObject.addProperty("Business_Segment", business_segment);
				jsonObject.addProperty("Account_Type", user_type);
				jsonObject.addProperty("Account_Sub_Type", accSubType);
				jsonObject.addProperty("Email_Opt_In", emailOpt);
				jsonObject.addProperty("Preferred_Language", prefLang);
				jsonObject.addProperty("Business_Organization", businessOrg);
				logger.debug("jsonObject"+jsonObject.toString());

			}

			if (state.equals(Basf_Constant.LITE_REGISTER)){
				jsonObject.addProperty("Request_Type", "Registration");
				jsonObject.addProperty("Request_Attribute", "Profile");//
				jsonObject.addProperty("EUPF_ID", sub);
				// email = "Dteliafaro@Ifa-Coop.Com";
				jsonObject.addProperty("Email_Address", email);
				jsonObject.addProperty("Contact_First_Name", firstname);
				jsonObject.addProperty("Contact_Last_Name", lastname);
				jsonObject.addProperty("Zipcode", mailing_postalCode);
				jsonObject.addProperty("Business_Segment", business_segment);
				jsonObject.addProperty("Account_Type", user_type);
				jsonObject.addProperty("Account_Sub_Type", accSubType);
				jsonObject.addProperty("Email_Opt_In", emailOpt);
				jsonObject.addProperty("Preferred_Language", prefLang);
				logger.debug("jsonObject"+jsonObject.toString());
			}

			if (state.equalsIgnoreCase(Basf_Constant.PARTNER)) {
				jsonObject.addProperty("Request_Type", "Registration");
				//jsonObject.addProperty("Request_Attribute", "Profile");//
				jsonObject.addProperty("EUPF_ID", sub);
				jsonObject.addProperty("Email_Address", email);
				jsonObject.addProperty("contact_id", contact_id);// from db
				jsonObject.addProperty("account_id", account_id);// from db
				jsonObject.addProperty("Account_Type", user_type);
				jsonObject.addProperty("Business_Segment", business_segment);
				jsonObject.addProperty("Contact_First_Name", firstname);
				jsonObject.addProperty("Contact_Last_Name", lastname);
				jsonObject.addProperty("Email_Opt_In", emailOpt);
				logger.debug("jsonObject"+jsonObject.toString());
			}
			if (state.equalsIgnoreCase(Basf_Constant.LOGIN)) {

				JsonObject requestJson = new JsonObject();
				requestJson.addProperty("sub", sub);
				requestJson.addProperty("type", "get_user_id_sub");
				JsonObject userIdJson = eupfService.callAwsRestService(requestJson);

				if (userIdJson.has(Basf_Constant.STATUS)
						&& userIdJson.get(Basf_Constant.STATUS).getAsString().equals(Basf_Constant.STATUS_SUCCESS)) {
					if (userIdJson.has(Basf_Constant.DATA)) {
						JsonArray userIdArray = userIdJson.get(Basf_Constant.DATA).getAsJsonArray();
						for (JsonElement userElement : userIdArray) {
							JsonObject userJson = userElement.getAsJsonObject();
							contact_id = "";
							if (userJson.has("contactid")) {
								contact_id = userJson.get("contactid").getAsString();
								//contact_id = "380113";
							}
							account_id = "";
							if (userJson.has("account_id")) {
								account_id = userJson.get("account_id").getAsString();
								//account_id = "298492";
							}
							lead_id = "";
							if(userJson.has("lead_id")){
								lead_id = userJson.get("lead_id").getAsString();
							}
							break;
						}
					}
				}

				jsonObject.addProperty("Request_Type", "Login");//
				jsonObject.addProperty("Request_Attribute", "Profile");//
				jsonObject.addProperty("EUPF_ID", sub);//
				jsonObject.addProperty("Email_Address", email);//
				if (StringUtils.isNotBlank(lead_id)) {
					jsonObject.addProperty("Lead_ID", lead_id);// from jwt
				}
				if(!contact_id.equals("")){
					jsonObject.addProperty("contact_id", contact_id);// from db//
				}
				if(!contact_id.equals("")){
					jsonObject.addProperty("account_id", account_id);// from db//
				}
//				jsonObject.addProperty("Contact_First_Name", firstname);
//				jsonObject.addProperty("Contact_Last_Name", lastname);
//				jsonObject.addProperty("Mailing_Address", mailing_address);
//				jsonObject.addProperty("City", mailing_city);
//				jsonObject.addProperty("State", mailing_state);
//				jsonObject.addProperty("Zipcode", mailing_postalCode);
//				jsonObject.addProperty("Country", country);
//				jsonObject.addProperty("Phone_Number", telephoneNumber);
//				jsonObject.addProperty("Business_Name", business_name);
//				jsonObject.addProperty("Business_Segment", business_segment);// from db

			}
		}

		return jsonObject;
	}

	@Override
	public JsonObject getRegistrationRequest(String jwt_token, String state) {

		JsonObject jsonObject = new JsonObject();

		jsonObject.addProperty("Request_Type", "Registration");
		jsonObject.addProperty("EUPF_ID", "EUPF132432");
		jsonObject.addProperty("Email_Address", "abffarms@yahoo.co");
		jsonObject.addProperty("Contact_First_Name", "Carol");
		jsonObject.addProperty("Contact_Last_Name", "McElroy");
		jsonObject.addProperty("Mailing_Address", "700 Eagle St");
		jsonObject.addProperty("City", "Duncombe");
		jsonObject.addProperty("State", "IA");
		jsonObject.addProperty("Zipcode", "50532");
		jsonObject.addProperty("Country", "US");
		jsonObject.addProperty("Phone_Number", "2523335167");
		jsonObject.addProperty("Business_Name", "New Cooperative Inc");
		jsonObject.addProperty("Business_Segment", "Crop (US)");
		jsonObject.addProperty("Account_Type", "Retailer");
//		jsonObject.addProperty("Contact_Id", "380113");//db
//		jsonObject.addProperty("Account_BASF_ID", "298492");//db

		return jsonObject;
	}


	@Override
	public JsonObject batchUpdate(int userUpdateLimit) {

		JsonObject requestJson = new JsonObject();
		JsonObject responseJson = new JsonObject();
		requestJson.addProperty("type", "get_status_pending_users");
		requestJson.addProperty("limit", userUpdateLimit+"");

		JsonObject talendJson = new JsonObject();
		talendJson.addProperty("Request_Type", "Batch");
		talendJson.addProperty("Request_Attribute", "APP");


		try {

			JsonObject requestBodyJsonObj= new JsonObject();
			requestBodyJsonObj.addProperty("grant_type", "client_credentials");
			requestBodyJsonObj.addProperty("client_id", authConfigService.getAzure_clientId());
			requestBodyJsonObj.addProperty("client_secret", authConfigService.getAzure_client_secret());
			requestBodyJsonObj.addProperty("scope", "https://graph.microsoft.com/.default");

			JsonObject tokenJson = appAccessTokenService.getAppAccessToken(requestBodyJsonObj);// take access token

			String jwttoken=tokenJson.get("access_token").getAsString();



			responseJson = eupfService.callAwsRestService(requestJson);
			JsonArray userIdArray= responseJson.get("data").getAsJsonArray();
			JsonArray contactJson = new JsonArray();
			for (JsonElement userElement : userIdArray) {
				try {
					JsonObject indcontactobj = new JsonObject();
					JsonObject userJson = userElement.getAsJsonObject();
					indcontactobj.addProperty("Eupf_Id", userJson.get("eupf_id").getAsString());
					indcontactobj.addProperty("Contact_Id", "");
					indcontactobj.addProperty("Account_Basf_ID", "");
					indcontactobj.addProperty("Lead_Id", userJson.get("lead_id").getAsString());
					contactJson.add(indcontactobj);
				}catch (Exception e){
					logger.debug(e.toString());
				}
			}
			talendJson.add("Contact_Data", contactJson);

			String jwt_token=jwttoken;//"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImtCcWUxaU1Ua1JqMlVSMHFrSnZ6aFBtWmpBQ1d1RGItckdFdzJQdmpoVWsifQ.eyJleHAiOjE2MTU0NjcyMjcsIm5iZiI6MTYxNTQyNDAyNywidmVyIjoiMS4wIiwiaXNzIjoiaHR0cHM6Ly9iYXNmdXNleHQuYjJjbG9naW4uY29tL2U2NjkwY2E4LWJiYTktNDk0Zi1hZGU4LTY0OTA1OWZmZjVjMi92Mi4wLyIsInN1YiI6IjhkNjgwMjQ3LWJjMzMtNDQ2MC05Y2YzLTBiZDM3Y2E1MGYwMCIsImF1ZCI6IjE3Y2M1YzVmLWUwZjUtNGNkOC1iMzE4LTgwMWI3NzA5NDVkZiIsIm5vbmNlIjoiODc4MzY1OCIsImlhdCI6MTYxNTQyNDAyNywiYXV0aF90aW1lIjoxNjE1NDI0MDExLCJlbWFpbCI6InN3YXBuaWwuZGVzYWlAYm9ybmdyb3VwLmNvbSIsImV4dGVuc2lvbl9UZXJtc0NvbnNlbnRTdGF0dXMiOnRydWUsIm1vYmlsZSI6IjEyMzQ1Njc4OTAiLCJmaXJzdG5hbWUiOiJTd2FwbmlsIiwibGFzdG5hbWUiOiJEZXNhaSIsImF0X2hhc2giOiI3ZGtWaWE0dU1Ka1lTZkNlandFYTB3In0.jYop5l11u49pJkgnOezcF9GZtsBDnRy5_twycXZePKpCXJ7u2BBu1B1Q57SBbstGG1bBYc8o0hMIBTs9bDeyECAZh3Ke4AZqHkZdXX8IsBwTO5i64DAsX-8aJrQfGmZkNpfYgqZ8SAPpTaTlEkh2SMmuRmBods2VKxMI_Jtezt9qzOkPMZWJTCibHDlwGLfpus1DVidKaz79G7LNZGSWPlZr4hEgJWx3xJf0H6nKOSRU5CAUZWDH_FfNuXZZNSgvxnGt17EONXL__YklfzH5HgOV20DZ5_GNvK6azhKIDotiFyFIV8gBXrFb-bwoH_hZmNv9kx8LpqKF8EJSZ_XdNw";//jwttoken;
			String xApiKey = authConfigService.getEupf_apigee_talend_key();
			String talendApiClientId = authConfigService.getEupf_apigee_talend_client_id();
			String url =authConfigService.getTalendEndpointUrl()+Basf_Constant.TALEND_BATCH_UPDATE_ENDPOINT;
			JsonObject talenbatchobj= talendAPIGeneric(talendApiClientId,  xApiKey,  url,  talendJson );
			logger.debug("MDM batch response: " + talenbatchobj.toString());
			contactJson = new JsonArray();
			for(JsonElement json : talenbatchobj.get("Contact_Data").getAsJsonArray()){
				try{
					if((!json.getAsJsonObject().get("account_id").isJsonNull()) &&
							(!json.getAsJsonObject().get("Contact_Id").isJsonNull() && !json.getAsJsonObject().get("Contact_Id").getAsString().equals(""))){
						contactJson.add(json.getAsJsonObject());
					}
				}catch (NullPointerException n){
					logger.debug(n.toString());
				}
			}
			talenbatchobj.add("Contact_Data",contactJson);
			JsonArray talendJsonresp = talenbatchobj.get("Contact_Data").getAsJsonArray();


			requestJson = new JsonObject();

			requestJson.addProperty("type", "update_status_pending_users");
			requestJson.add("update_query", talenbatchobj);
			try {
				responseJson = eupfService.callAwsRestService(requestJson);

			} catch(Exception e) {

			}

			String extn_no= authConfigService.getAzure_extension_no();

			for (JsonElement userElement : talendJsonresp) {
				JsonObject userJson = userElement.getAsJsonObject();
				JsonObject userJson1 = new JsonObject();
				userJson1.addProperty("id", userJson.get("Eupf_Id").getAsString());
				userJson1.addProperty("extension_"+extn_no+"_account_number", userJson.get("account_id").getAsString());
				userJson1.addProperty("extension_"+extn_no+"_contact_id", userJson.get("Contact_Id").getAsString());
				//userJson1.addProperty("extension_"+extn_no+"_account_number", "12345");
				//userJson1.addProperty("extension_"+extn_no+"_contact_id", "6789");

				if(userJson.has("Status")) {
					String status=userJson.get("Status").getAsString();
					if(status.equalsIgnoreCase("Rejected By DMT")) {
						userJson1.addProperty("accountEnabled", false);
					}
					if(userJson.get("Status").getAsString().equalsIgnoreCase("Rejected By DMT") || userJson.get("Status").getAsString().equalsIgnoreCase("Linked")) {
						userDetailsService.updateUser(userJson.get("Eupf_Id").getAsString(), userJson1, jwttoken);
					}
				}

			}



		} catch (Exception e) {
			logger.debug("exception occoured");

		}
		return responseJson;
	}



}
