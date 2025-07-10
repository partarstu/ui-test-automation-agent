/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.rag.model.UiElement;

import java.util.Comparator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class ChromaRetriever implements UiElementRetriever {
    private static final Logger LOG = LoggerFactory.getLogger(ChromaRetriever.class);
    private static final String COLLECTION_NAME = "ui_elements";
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    public ChromaRetriever(String url) {
        checkArgument(isNotBlank(url));
        try {
            embeddingStore = ChromaEmbeddingStore
                    .builder()
                    .baseUrl(url)
                    .collectionName(COLLECTION_NAME)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        } catch (RuntimeException e) {
            String errorMessage = String.format("Failed to connect to ChromaDB at URL: %s. Root cause: ", url);
            LOG.error(errorMessage, e);
            throw e;
        }
    }

    @Override
    public void storeElement(UiElement uiElement) {
        var segment = uiElement.asTextSegment();
        var embedding = embeddingModel.embed(segment).content();
        embeddingStore.addAll(List.of(uiElement.uuid().toString()), List.of(embedding), List.of(segment));
        LOG.info("Inserted UiElement '{}' into the vector DB", uiElement.name());
    }

    @Override
    public List<RetrievedItem> retrieveElementsByScore(String query, int topN, double minScore) {
        var queryEmbedding = embeddingModel.embed(query).content();
        var searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .minScore(minScore)
                .maxResults(topN)
                .build();
        var result = embeddingStore.search(searchRequest);
        var resultingItems = result.matches().stream()
                .sorted(Comparator.<EmbeddingMatch<TextSegment>>comparingDouble(EmbeddingMatch::score).reversed())
                .map(match -> new RetrievedItem(UiElement.fromTextSegment(match.embedded()), match.score()))
                .distinct()
                .toList();
        LOG.info("Retrieved {} most matching results to the query '{}'", resultingItems.size(), query);
        return resultingItems;
    }

    @Override
    public void updateElement(UiElement originalUiElement, UiElement updatedUiElement) {
        removeElement(originalUiElement);
        storeElement(updatedUiElement);
    }

    @Override
    public void removeElement(UiElement uiElement) {
        embeddingStore.remove(uiElement.uuid().toString());
        LOG.info("Removed UiElement '{}' from the vector DB", uiElement.name());
    }
}