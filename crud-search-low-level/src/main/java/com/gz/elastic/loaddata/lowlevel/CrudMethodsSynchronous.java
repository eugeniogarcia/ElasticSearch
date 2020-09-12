package com.gz.elastic.loaddata.lowlevel;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gz.elastic.loaddata.crud.ClientCrudMethods;
import com.gz.elastic.loaddata.helper.CatalogItemSearchResult;
import com.gz.elastic.model.CatalogItem;


public class CrudMethodsSynchronous implements ClientCrudMethods {

	static Logger LOG = LogManager.getLogger(CrudMethodsSynchronous.class);
	
	protected static final String HITS = "\"hits\":[";
	protected static final String _SOURCE = "\"_source\":";	
	protected static final String SEARCH_BY_ID = "{\r\n" + 
			"  \"query\": {\r\n" + 
			"    \"terms\": {\r\n" + 
			"      \"_id\": [ \"%s\" ] \r\n" + 
			"    }\r\n" + 
			"  }\r\n" + 
			"}";
	protected static final String SEARCH_FULL_TEXT = "{\r\n" + 
			"    \"query\": {\r\n" + 
			"        \"query_string\" : {\r\n" + 
			"            \"query\" :  \"%s\"\r\n" + 
			"        }\r\n" + 
			"    }\r\n" + 
			"}";

	protected static final String SEARCH_CATEGORY_NAME_TEXT ="{\r\n" + 
			"    \"query\": {\r\n" + 
			"        \"match\" : {\r\n" + 
			"            \"category.category_name\" : \"%s\"\r\n" + 
			"        }\r\n" + 
			"    }\r\n" + 
			"}";
	
	private final String index;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	
	public CrudMethodsSynchronous(String index, RestClient restClient) {
		this.index = index;
		this.restClient = restClient;
		this.objectMapper = new ObjectMapper();
	}
	
	
	protected String getIndex() {
		return index;
	}


	protected RestClient getRestClient() {
		return restClient;
	}


	protected ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	@Override
	public void createCatalogItem(List<CatalogItem> items) {
		items.stream().forEach(e-> {
			
			//Hacemos una petición PUT. Construye la uri
			Request request = new Request("PUT", String.format("/%s/_doc/%d", getIndex(),  e.getId()));
			
			try {
				request.setJsonEntity(getObjectMapper().writeValueAsString(e));
			
				getRestClient().performRequest(request);
			} catch (IOException ex) {
				LOG.warn("Could not post {} to ES", e, ex);
			}
		});
	}
	
	
	/**
	 * Alternative method of getting an item by id
	 * @param idToFind id of an item to be found
	 * @return item in the index based on specified id
	 */
	public Optional<CatalogItem> getCatalogItemByIdOther(Integer idToFind) {

		Request request = new Request("GET", String.format("/%s/_search", getIndex()));
		request.setJsonEntity(String.format(SEARCH_BY_ID, idToFind));
		try {
			Response response = getRestClient().performRequest(request);
			if (response.getStatusLine().getStatusCode()==200) {
				CatalogItem catalogItem = parseResultsFromJson(response, _SOURCE);
				return Optional.of(catalogItem);
			} 
		} catch (IOException ex) {
			LOG.warn("Could not post {} to ES", idToFind, ex);
		}
		return Optional.empty();
	}

	protected CatalogItem parseResultsFromJson(Response response, String resultStartingPoint)
			throws IOException, JsonParseException, JsonMappingException {
		String responseBody = EntityUtils.toString(response.getEntity());
		LOG.debug("Got result as {}", responseBody);
		int startIndex = responseBody.indexOf(resultStartingPoint);
		int endIndex = responseBody.indexOf("}]}}");
		String json = responseBody.substring(startIndex + resultStartingPoint.length(), endIndex);
		
		CatalogItem catalogItem = getObjectMapper().readValue(json, CatalogItem.class);
		return catalogItem;
	}
	
	/**
	 * @inheritDoc
	 */
	@Override
	public Optional<CatalogItem> getCatalogItemById(Integer idToFind) {
		Request request = new Request("GET", String.format("/%s/_doc/%d", getIndex(), idToFind));
		try {
			Response response = getRestClient().performRequest(request);
			if (response.getStatusLine().getStatusCode()==200) {
				String responseBody = EntityUtils.toString(response.getEntity());
				LOG.debug("find by item id response: {}", responseBody);
				int startIndex = responseBody.indexOf(_SOURCE);
				int endIndex = responseBody.indexOf("}}");
				String json = responseBody.substring(startIndex + _SOURCE.length(), endIndex+2);
				LOG.debug(json);
				CatalogItem catalogItem = getObjectMapper().readValue(json, CatalogItem.class);
				return Optional.of(catalogItem);
			} 
		} catch (IOException ex) {
			LOG.warn("Could not post {} to ES", idToFind, ex);
		}
		return Optional.empty();
	}
	
	/**
	 * @inheritDoc
	 */
	@Override
	public List<CatalogItem> findCatalogItem(String text) {
		Request request = new Request("GET", String.format("/%s/_search", getIndex()));
		request.setJsonEntity(String.format(SEARCH_FULL_TEXT, text));
		try {
			Response response = getRestClient().performRequest(request);
			if (response.getStatusLine().getStatusCode()==200) {
				List<CatalogItem> catalogItems = parseResultsFromFullSearch(response);
				return catalogItems;
			} 
		} catch (IOException ex) {
			LOG.warn("Could not post {} to ES", text, ex);
		}
		return Collections.emptyList();
		
	}

	protected List<CatalogItem> parseResultsFromFullSearch(Response response)
			throws IOException, JsonParseException, JsonMappingException {
		String responseBody = EntityUtils.toString(response.getEntity());
		LOG.debug(responseBody);
		int startIndex = responseBody.indexOf(HITS);
		int endIndex = responseBody.indexOf("]}}");
		String json = responseBody.substring(startIndex, endIndex+1);
		json = "{" + json + "}";
		
		CatalogItemSearchResult sr = getObjectMapper().readValue(json, CatalogItemSearchResult.class);
		List<CatalogItem> catalogItems = sr.getHits().stream().map(e-> e.getSource()).collect(Collectors.toList());
		return catalogItems;
	}
	
	/**
	 * @inheritDoc
	 */
	@Override
	public List<CatalogItem> findCatalogItemByCategory(String token) {
		Request request = new Request("GET", String.format("/%s/_search", getIndex()));
		request.setJsonEntity(String.format(SEARCH_CATEGORY_NAME_TEXT, token));
		try {
			Response response = getRestClient().performRequest(request);
			if (response.getStatusLine().getStatusCode()==200) {
				List<CatalogItem> catalogItems = parseResultsFromFullSearch(response);
				return catalogItems;
			} 
		} catch (IOException ex) {
			LOG.warn("Could not post {} to ES", token, ex);
		}
		return Collections.emptyList();
		
		
	}
	
	/**
	 * @inheritDoc
	 */
	@Override
	public void updateCatalogItem(CatalogItem item) {
		Request request = new Request("POST", String.format("/%s/_update/%d", getIndex(),  item.getId()));
		try {
			request.setJsonEntity("{ \"doc\" :" + 
					getObjectMapper().writeValueAsString(item)+"}");
		
			Response response = getRestClient().performRequest(request);
			LOG.debug("update response: {}", response);
		} catch (IOException ex) {
			LOG.warn("Could not post {} to ES", item, ex);
		}
		
	}
	
	/**
	 * @inheritDoc
	 */
	@Override
	public void updateCatalogItemDescription(Integer id, String description) {
		Request request = new Request("POST", String.format("/%s/_update/%d", getIndex(),  id));
		try {
			request.setJsonEntity(String.format("{ \"doc\" : { \"description\" : \"%s\" }}", description));
		
			Response response = getRestClient().performRequest(request);
			LOG.debug("update response: {}", response);
		} catch (IOException ex) {
			LOG.warn("Could not post {} to ES", id, ex);
		}
	}
	
	/**
	 * @inheritDoc
	 */
	@Override
	public void deleteCatalogItem(Integer id) {
		Request request = new Request("DELETE", String.format("/%s/_doc/%d", getIndex(),  id));
		try {
			Response response = getRestClient().performRequest(request);
			LOG.debug("delete response: {}", response);
		} catch (IOException ex) {
			LOG.warn("Could not post {} to ES", id, ex);
		}
	}
	
	
}
