package com.tuling.tulingmall.search.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.tuling.tulingmall.search.service.EsClientSearchService;

/**
 * @author ????????????
 */
@Service(value="esSearchService")
public class EsClientClientSearchServiceImpl implements EsClientSearchService {

    @Qualifier("restHighLevelClient")
    @Autowired
    private RestHighLevelClient client;


    @Override
    public boolean createIndex(String index) throws IOException {
        //1.??????????????????
        CreateIndexRequest request = new CreateIndexRequest(index);
        //2.?????????????????????IndicesClient,?????????????????????
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        return response.isAcknowledged();
    }


    @Override
    public boolean isExit(String index) throws IOException {
        GetIndexRequest request = new GetIndexRequest(index);
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        return exists;
    }


    @Override
    public boolean delete(String index) throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(index);
        AcknowledgedResponse delete = client.indices().delete(request, RequestOptions.DEFAULT);
        return delete.isAcknowledged();
    }


    @Override
    public boolean addDocument(String index, String id, Object object) throws IOException {
        IndexRequest request = new IndexRequest(index);
        //?????? ????????????????????? put /index/_doc/1
        request.id(id);//???????????????id?????????????????????id
        request.timeout("1s");//??????????????????
        System.out.println("JSON.toJSONString(object):"+JSON.toJSONString(object));
        //??????????????????????????????Json???
        request.source(JSON.toJSONString(object), XContentType.JSON);
        //?????????????????????,?????????????????????
        IndexResponse response = client.index(request, RequestOptions.DEFAULT);
        return response.getShardInfo().getSuccessful()>0?true:false;
    }


    @Override
    public boolean isdocuexit(String index, String id) throws IOException {
        GetRequest getRequest = new GetRequest(index,id);
        //??????????????????_source?????????
        getRequest.fetchSourceContext(new FetchSourceContext(false));
        getRequest.storedFields("_none_");
        return client.exists(getRequest, RequestOptions.DEFAULT);
    }


    @Override
    public String getDoucumment(String index, String id) throws IOException {
        GetRequest getRequest = new GetRequest(index, id);
        GetResponse response = client.get(getRequest, RequestOptions.DEFAULT);
        return response.getSourceAsString();
    }


    @Override
    public boolean updateDocument(Object object, String index, String id) throws IOException {
        UpdateRequest request = new UpdateRequest(index, id);
        request.timeout("1s");

        request.doc(JSON.toJSONString(object), XContentType.JSON);
        UpdateResponse update = client.update(request, RequestOptions.DEFAULT);
        return update.getShardInfo().getSuccessful()>0?true:false;
    }


    @Override
    public boolean deleteDocument(String index, String id) throws IOException{
        DeleteRequest request = new DeleteRequest(index,id);
        request.timeout("1s");
        DeleteResponse deleteResponse = client.delete(request, RequestOptions.DEFAULT);
        return deleteResponse.getShardInfo().getSuccessful()>0?true:false;
    }


    @Override
    public boolean addmoredocument(List<Object> list, String index, String id) throws IOException{
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.timeout("1s");
        //??????????????????
        for (int i=0;i<list.size();i++){
            bulkRequest.add(
                    new IndexRequest(index)
                            //.id(id)
                            .source(JSON.toJSONString(list.get(i)), XContentType.JSON));
        }
        BulkResponse responses = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        //???????????? false-????????????
        return responses.hasFailures()?false:true;
    }


    @Override
    public List<Map<String,Object>> termQuery(String index, TreeMap<String, Object> content, int size, int from, boolean ishigh) throws IOException {
        SearchRequest searchRequest = new SearchRequest(index);
        //??????????????????
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        System.out.println(content.firstKey());
        //????????????
        TermQueryBuilder termQueryBuilder = QueryBuilders.termQuery(content.firstKey(),content.get(content.firstKey()));
        sourceBuilder.query(termQueryBuilder);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));


        //?????????????????????
        sourceBuilder.size(size);
        //??????????????????
        sourceBuilder.from(from);
        //?????????????????????????????????????????????????????????
        if (ishigh){
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            //?????????????????????
            highlightBuilder.field(content.firstKey());
            //?????????????????????????????????,????????????????????????????????????
            sourceBuilder.highlighter(highlightBuilder);
        }
        System.out.println("dsl===1==="+sourceBuilder.toString());
        //????????????????????????????????????
        searchRequest.source(sourceBuilder);


        //?????????????????????
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHit[] hits = searchResponse.getHits().getHits();

        ArrayList<Map<String,Object>> result = new ArrayList<>();
        for (SearchHit searchHit:hits){
            Map<String, HighlightField> highlightFields = searchHit.getHighlightFields();
            //?????????????????????
            HighlightField property = highlightFields.get(content.firstKey());
            //?????????????????????(????????????)
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();
            if (ishigh){
                if (property!=null){
                    Text[] fragments = property.fragments();
                    String n_title = "";
                    for (Text text:fragments){
                        n_title += text;
                    }
                    sourceAsMap.put(content.firstKey(),n_title);
                }
            }


            //2.2 ??????????????????????????????
          /*  HighlightField name = hit.getHighlightFields().get("name");
            //2.3 ??????name?????????????????????????????????(???????????????????????????????????????????????????????????????????????????????????????????????????name???????????????)
            String nameValue = name!=null ? name.getFragments()[0].string() : esModel.getName();
            esModel.setName(nameValue);*/

            result.add(sourceAsMap);
        }
        System.out.println("result==1===:"+result);
        return result;
    }


    @Override
    public List<Map<String,Object>> matchQuery(String index, TreeMap<String, Object> content, int size, int from, boolean ishigh) throws IOException {
        SearchRequest searchRequest = new SearchRequest(index);
        //??????????????????
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //????????????
        MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery(content.firstKey(),content.get(content.firstKey()));

        sourceBuilder.query(matchQueryBuilder);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));


        //?????????????????????
        sourceBuilder.size(size);
        //??????????????????
        sourceBuilder.from(from);
        //???????????????
        if (ishigh){
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            //?????????????????????
            highlightBuilder.field(content.firstKey());
            //?????????????????????????????????,????????????????????????????????????
            sourceBuilder.highlighter(highlightBuilder);
        }

        System.out.println("dsl===2==="+sourceBuilder.toString());

        //????????????????????????????????????
        searchRequest.source(sourceBuilder);
        //?????????????????????
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHit[] hits = searchResponse.getHits().getHits();

        ArrayList<Map<String,Object>> result = new ArrayList<>();
        for (SearchHit searchHit:hits){
            Map<String, HighlightField> highlightFields = searchHit.getHighlightFields();
            //?????????????????????
            HighlightField property = highlightFields.get(content.firstKey());
            //?????????????????????(????????????)
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();
            System.out.println("sourceAsMap===2==="+sourceAsMap);
            if (ishigh){
                if (property!=null){
                    Text[] fragments = property.fragments();
                    String n_title = "";
                    for (Text text:fragments){
                        n_title += text;
                    }
                    sourceAsMap.put(content.firstKey(),n_title);
                }
            }
            result.add(sourceAsMap);
        }
        System.out.println("result==2===:"+result);

        return result;
    }



    @Override
    public List<Map<String,Object>> boolmustQuery(String index, TreeMap<String, Object> content, int size, int from, boolean ishigh) throws IOException {
        SearchRequest searchRequest = new SearchRequest(index);
        //??????????????????
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //????????????
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        Set keys = content.keySet();
        for (Object key:keys){
            //???????????????????????????
            boolQueryBuilder.must(QueryBuilders.termQuery((String) key,content.get(key)));
        }
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        //?????????????????????
        sourceBuilder.size(size);
        //??????????????????
        sourceBuilder.from(from);

        System.out.println("dsl===3==="+sourceBuilder.toString());
        //????????????????????????????????????
        searchRequest.source(sourceBuilder);


        //?????????????????????
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHit[] hits = searchResponse.getHits().getHits();

        ArrayList<Map<String,Object>> result = new ArrayList<>();
        for (SearchHit searchHit:hits){
            Map<String, HighlightField> highlightFields = searchHit.getHighlightFields();
            //?????????????????????
            HighlightField property = highlightFields.get(content.firstKey());
            //?????????????????????(????????????)
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();
            System.out.println("sourceAsMap===2==="+sourceAsMap);
            if (ishigh){
                if (property!=null){
                    Text[] fragments = property.fragments();
                    String n_title = "";
                    for (Text text:fragments){
                        n_title += text;
                    }
                    sourceAsMap.put(content.firstKey(),n_title);
                }
            }
            result.add(sourceAsMap);
        }

        System.out.println("result==3===:"+result);
        return result;
    }
}



