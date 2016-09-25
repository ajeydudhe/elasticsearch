/********************************************************************
 * File Name:    IndexMigrationConfigurationService.java
 *
 * Date Created: Sep 23, 2016
 *
 * ------------------------------------------------------------------
 * 
 * Copyright @ 2016 ajeydudhe@gmail.com
 *
 *******************************************************************/

package my.elasticsearch.plugins;

import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.metadata.MetaDataCreateIndexService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.slf4j.Slf4jESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.Engine.Create;
import org.elasticsearch.index.engine.Engine.Delete;
import org.elasticsearch.index.engine.Engine.Index;
import org.elasticsearch.index.engine.Engine.IndexingOperation;
import org.elasticsearch.index.indexing.IndexingOperationListener;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.IndicesService;

public class IndexMigrationConfigurationService extends AbstractIndexLifecycleComponent<IndexMigrationConfigurationService>
{
  @Inject
  public IndexMigrationConfigurationService(final Settings settings, 
                                            final IndicesService indicesService,
                                            final MetaDataCreateIndexService metaDataCreateIndexService,
                                            final ClusterService clusterService)
  {
    super(settings, indicesService, MIGRATION_INDEX, true);
    
    this.metaDataCreateIndexService = metaDataCreateIndexService;
    this.clusterService = clusterService;
  }
  
  @Override
  protected void afterIndexShardStarted(final IndexShard indexShard)
  {
    indexingOperationListener = getIndexingOperationListener();
    indexShard.indexingService().addListener(indexingOperationListener);
    
    LOGGER.error("### Started monitoring migration configuration changes in {}.", MIGRATION_INDEX);        
  }

  @Override
  protected void afterIndexShardDeleted(ShardId shardId, Settings indexSettings)
  {
    indexingOperationListener = null;
    
    // TODO: Ajey - Will the listener be removed since the index is deleted?
    
    LOGGER.error("### Stopped monitoring migration configuration changes in {}.", MIGRATION_INDEX);        
  }
  
  private IndexingOperationListener getIndexingOperationListener()
  {
    // TODO: Ajey - We should listen only on primary shard. Documents added to backup shard may also trigger the create/index events.
    return new IndexingOperationListener()
    {
      @Override
      public Create preCreate(final Create create)
      {
        logDocumentInfo("### preCreate", create);
        
        throw new RuntimeException(String.format("Create the migration configuration as /%s/sourceIndex/targetIndex", MIGRATION_INDEX));
      }
      
      @Override
      public Index preIndex(final Index index)
      {
        // Both type and id should be valid index names since type maps to source index and id to target index.
        metaDataCreateIndexService.validateIndexName(index.type(), clusterService.state());
        metaDataCreateIndexService.validateIndexName(index.id(), clusterService.state());

        return super.preIndex(index);
      }
      
      @Override
      public void postIndex(final Index index)
      {
        logDocumentInfo("### postIndex", index);
                
        super.postIndex(index);
      }
      
      @Override
      public void postDelete(final Delete delete)
      {
        //logDocumentInfo("### postDelete", delete);
        super.postDelete(delete);
      }
      
      private void addIndexSynchronizer(final Index index)
      {
      }
      
      private String key(final String sourceIndex, final String targetIndex)
      {
        return sourceIndex + "#" + targetIndex;
      }
      
      private void logDocumentInfo(final String context, final IndexingOperation operation)
      {
        LOGGER.error("### {}\n_type: {}\n_id: {}\n_version: {}\n_versionType: {}\nstartTime: {}\nOrigin: {}\nsource: \n{}",
                     context,
                     operation.type(), 
                     operation.id(),
                     operation.version(),
                     operation.versionType(),
                     operation.startTime(),
                     operation.origin(),
                     operation.parsedDoc().source().toUtf8());
      }
    };
  }
  
  // Private
  private final MetaDataCreateIndexService metaDataCreateIndexService;
  private final ClusterService clusterService;
  private IndexingOperationListener indexingOperationListener;
  //private Map<String, IndexSynchronizer> indexSynchronizers = new HashMap<>();
  
  //private final Object SYNC_BLOCK = new Object();
  
  private final static String MIGRATION_INDEX = "es_migration";
    
  private final static ESLogger LOGGER = Slf4jESLoggerFactory.getLogger("IndexMigrationConfigurationService");
}

