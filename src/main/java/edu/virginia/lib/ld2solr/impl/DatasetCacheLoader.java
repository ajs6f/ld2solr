package edu.virginia.lib.ld2solr.impl;

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.JdkFutureAdapters.listenInPoolThread;
import static com.hp.hpl.jena.query.ReadWrite.WRITE;
import static com.hp.hpl.jena.shared.Lock.READ;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

import org.slf4j.Logger;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

import edu.virginia.lib.ld2solr.spi.CacheLoader;
import edu.virginia.lib.ld2solr.spi.ThreadedStage;

/**
 * A {@link DatasetCacheLoader} loads Linked Data into a Jena {@link Dataset} .
 * 
 * @author ajs6f
 * 
 */
public class DatasetCacheLoader extends ThreadedStage<DatasetCacheLoader, Void> implements
		CacheLoader<DatasetCacheLoader, Dataset> {

	private CompletionService<Model> internalQueue;

	private Dataset dataset;

	private Set<Resource> successfullyLoadedResources;

	private String accepts = null;

	private static final Logger log = getLogger(DatasetCacheLoader.class);

	public DatasetCacheLoader() {
		this.internalQueue = new ExecutorCompletionService<Model>(this.threadpool);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see CacheLoader#load(java.util.Set)
	 */
	@Override
	public Set<Resource> load(final Iterable<Resource> uris) {
		successfullyLoadedResources = new HashSet<>();
		for (final Resource uri : uris) {
			log.info("Queueing retrieval task for URI: {}...", uri);
			final Future<Model> loadFuture = internalQueue.submit(new JenaModelTriplesRetriever(accepts).apply(uri));
			final ListenableFuture<Model> loadTask = listenInPoolThread(loadFuture);
			addCallback(loadTask, new FutureCallback<Model>() {

				@Override
				public void onSuccess(final Model result) {
					log.info("Retrieved URI: {} and will add its contents to cache.", uri);
					successfullyLoadedResources.add(uri);
				}

				@Override
				public void onFailure(final Throwable t) {
					log.error("Failed to retrieve: {}!", uri);
					log.error("Exception: ", t);
				}
			});
		}
		log.info("Finished queuing retrieval tasks.");
		// the only purpose of this loop is to ensure that we execute as many
		// tasks to add triples to the cache as we have executed tasks to
		// retrieve triples
		for (@SuppressWarnings("unused") final Resource uri : uris) {
			try {
				final Model m = internalQueue.take().get();
				if (!m.isEmpty()) {
					m.enterCriticalSection(READ);
					log.debug("Adding {} triples to cache...", m.size());
					log.trace("Adding triples: {}", m);
					try {
						dataset.begin(WRITE);
						try {
							dataset.getDefaultModel().add(m);
							dataset.commit();
						} catch (final Exception e) {
							log.error("Error adding triples to cache!");
							log.error("Triples: ", m);
							log.error("Exception: ", e);
						} finally {
							dataset.end();
						}
					} finally {
						m.leaveCriticalSection();
						m.close();
					}
				}
			} catch (InterruptedException | ExecutionException e) {
				log.error("Error assembling triples to add to cache!");
				log.error("Exception: ", e);
			}
		}
		return successfullyLoadedResources;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ThreadedStage#threads(java.lang.Integer)
	 */
	@Override
	public DatasetCacheLoader threads(final Integer numThreads) throws InterruptedException {
		super.threads(numThreads);
		this.internalQueue = new ExecutorCompletionService<Model>(this.threadpool);
		return this;
	}

	/**
	 * @param accepts
	 *            the HTTP Accepts header to use in retrieving Linked Data
	 *            resources
	 * @return this {@link DatasetCacheLoader} for further operation
	 */
	public DatasetCacheLoader accepts(final String accepts) {
		this.accepts = accepts;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see CacheLoader#cache(java.lang.Object)
	 */
	@Override
	public DatasetCacheLoader cache(final Dataset d) {
		this.dataset = d;
		return this;
	}
}