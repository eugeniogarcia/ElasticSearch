package com.gz.elastic.loaddata.helper;

import java.util.List;

import com.gz.elastic.model.CatalogItem;



public class CatalogItemSearchResult extends Hits<SearchResult<CatalogItem>> {
	
}

class Hits<T> {
	private List<T> hits;

	public List<T> getHits() {
		return hits;
	}

	public void setHits(List<T> hits) {
		this.hits = hits;
	}
	
}
