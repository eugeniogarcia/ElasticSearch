package com.gz.elastic.loaddata.lowlevel;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpHost;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.RestClient;

import com.gz.elastic.model.CatalogItem;
import com.gz.elastic.model.CatalogItemUtil;

/**
 * Low-level client
 */
public class LowLevelCrudApp {
	static Logger LOG = LogManager.getLogger(LowLevelCrudApp.class);

	public static void main( String[] args ) {
		//Se conecta con el host de Elastic Search. Cierra automáticamente el cliente cuando salimos de este bloque de código
		//, o se produce una excepción
		try(RestClient client = RestClient.builder(new HttpHost("localhost", 9200, "http")).build()) {
			
			//Vamos a trabajar con el indice catalog_item_low_level
			CrudMethodsSynchronous scm = new CrudMethodsSynchronous("catalog_item_low_level",  client);
			
			//Creamos items en el indice
			scm.createCatalogItem(CatalogItemUtil.getCatalogItems());
			
			//Buscamos un item
			List<CatalogItem> items = scm.findCatalogItem("flashlight");
			LOG.info("Found {} items: {}", items.size(), items);
			
			
			items = scm.findCatalogItemByCategory("House");
			LOG.info("Found {} items by category: {}", items.size(), items);
			
			CatalogItem itemForUpdate = CatalogItemUtil.getCatalogItems().get(3);
			itemForUpdate.setDescription("Updated : " + itemForUpdate.getDescription());
			scm.updateCatalogItem(itemForUpdate);
			
			Optional<CatalogItem> foundItem = scm.getCatalogItemById(itemForUpdate.getId());
			if (foundItem.isPresent()) {
				CatalogItem item = foundItem.get();
				LOG.info("Found item with id {} it is {}", item.getId(), item );
			} else {
				LOG.warn("Could not find in item");
			}
			
			scm.updateCatalogItemDescription(4, "Overwritten description");
			
			scm.deleteCatalogItem(2);
			Optional<CatalogItem> deletedItem = scm.getCatalogItemById(2);
			if (foundItem.isPresent()) {
				CatalogItem item = foundItem.get();
				LOG.info("Found item with id {} it is {}", item.getId(), item );
			} else {
				LOG.warn("Could not find in item");
			}
			
			
			CrudMethodsAsynchronous ascm = new CrudMethodsAsynchronous("catalog_item_low_level_async",  client);
			ascm.createCatalogItem(CatalogItemUtil.getCatalogItems());
			
		} catch(IOException e) {
			LOG.error("Error while accessing ES", e);
		}
    }
     
	
}
