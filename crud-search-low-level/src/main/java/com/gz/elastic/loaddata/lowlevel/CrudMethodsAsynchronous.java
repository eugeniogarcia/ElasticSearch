package com.gz.elastic.loaddata.lowlevel;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gz.elastic.model.CatalogItem;

/**
 * Example of low-level asynchronous client CRUD methods
 * @author Henry
 */
public class CrudMethodsAsynchronous {
	static Logger LOG = LogManager.getLogger(CrudMethodsAsynchronous.class);
	
	private final String index;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	
	public CrudMethodsAsynchronous(String index, RestClient restClient) {
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

	/** 
	 * Adds an items to Elasticsearch index
	 * @param items items to be added
	 */
	public void createCatalogItem(List<CatalogItem> itemsToCreate) {
		CountDownLatch countDownLatch = new CountDownLatch(itemsToCreate.size());
		ResponseListener responseListener = new ResponseListener() {
			@Override
			public void onSuccess(Response response) {
				countDownLatch.countDown();
			}
			@Override
			public void onFailure(Exception exception) {
				countDownLatch.countDown();
				LOG.error("Could not process ES request. Error : ", exception);
			}
		};
		
		itemsToCreate.stream().forEach(e-> {
			Request request = new Request("PUT", String.format("/%s/_doc/%d", getIndex(),  e.getId()));
			try {
				request.setJsonEntity(getObjectMapper().writeValueAsString(e));
				getRestClient().performRequestAsync(request, responseListener);
			} catch (IOException ex) {
				LOG.warn("Could not post {} to ES", e, ex);
			}
		});
		try {
			countDownLatch.await(); //wait for all the threads to finish
			LOG.info("Done inserting all the records to the index {}. Total # of records inserted is {}", getIndex(), itemsToCreate);
		} catch (InterruptedException e1) {
			LOG.warn("Got interrupted waiting for all the clients",e1);
		}
	}

	
	
	
}
