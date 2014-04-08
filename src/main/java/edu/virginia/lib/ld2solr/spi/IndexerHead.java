/**
 * 
 */
package edu.virginia.lib.ld2solr.spi;

import com.google.common.base.Function;
import com.hp.hpl.jena.rdf.model.Resource;

import edu.virginia.lib.ld2solr.api.NamedFields;

/**
 * An {@link IndexerHead} is moved in a path across an LDPath backend and
 * executes a single indexing tranformation, producing a {@link NamedFields} as
 * the result of each execution. The path of the head is normally a collection
 * of {@link Resource}s.
 * 
 * @author ajs6f
 * 
 */
public interface IndexerHead extends Function<Resource, NamedFields> {

}
