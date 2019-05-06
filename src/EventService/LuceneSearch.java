package EventService;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A singleton class that uses Lucene Library to a keyword search on the event map
 *
 * @author Hassan Chadad
 */
public class LuceneSearch {

    private static volatile LuceneSearch instance;
    private static Directory directory;
    private static Analyzer analyzer;

    /**
     * Private Constructor
     */
    private LuceneSearch() {
        directory = new RAMDirectory();
        analyzer = new StandardAnalyzer();
    }

    /**
     * A method that guarantees singleton mechanism
     * @return
     */
    public static LuceneSearch getInstance() {
        if (instance == null) {
            synchronized(LuceneSearch.class) {
                if (instance == null)
                    instance = new LuceneSearch();
            }
        }
        return instance;
    }

    /**
     * A method that parses the event map to get the values, then it creates a document of TextFields
     * containing the values. The document is added to the IndexWriter that takes the analyzer and Directory
     * to index the document and analyze it.
     *
     * @param eventMap
     */
    private void buildIndex(SortedMap<Integer, String []> eventMap) {
        try {
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
            IndexWriter writer = new IndexWriter(directory, indexWriterConfig);
            writer.deleteAll();
            for (int key : eventMap.keySet()) {
                Document document = new Document();
                document.add(new TextField("id", key + "", Field.Store.YES));
                document.add(new TextField("name", eventMap.get(key)[0], Field.Store.YES));
                document.add(new TextField("userid", eventMap.get(key)[1], Field.Store.YES));
                document.add(new TextField("avail", eventMap.get(key)[2], Field.Store.YES));
                document.add(new TextField("purchase", eventMap.get(key)[3], Field.Store.YES));
                document.add(new TextField("status", "all", Field.Store.NO));
                writer.addDocument(document);
            }
            writer.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    /**
     * A method that handles the keywords given and parse them as a query to search on.
     * If avail was 1 it means that the search will exclude the events with 0 available tickets (since they are not
     * available), otherwise it will seach for everything.
     *
     * @param keywords - keywords targeting event name
     * @param avail - to search on available events
     * @param field - field index to search at
     * @return Query presentation
     */
    private Query getQuery(String field, String keywords, int avail) {
        try {
            keywords = keywords.trim();
            QueryParser parser = new QueryParser(field, analyzer);
            BooleanQuery.Builder query = new BooleanQuery.Builder();
            Query keywordsQuery = parser.parse(keywords);

            PhraseQuery.Builder phraseQuery = new PhraseQuery.Builder();
            String [] terms = keywords.split(" "); // split keywords into array of terms ex: Test Event -> ["Test","Event"]
            for(String t : terms)
                phraseQuery.add(new Term(field, t)); // add terms to phraseQuery
            Query phraseQ = phraseQuery.build();

            TermQuery termQueryAvail;
            if(avail > 0) {
                termQueryAvail = new TermQuery(new Term("avail", "0")); // add term to search in avail field
                query.add(termQueryAvail, BooleanClause.Occur.MUST_NOT); // make the occur (Must NOT), it means anything but 0
            }
            query.add(keywordsQuery, BooleanClause.Occur.MUST);
            query.add(phraseQ, BooleanClause.Occur.SHOULD);
            query.setMinimumNumberShouldMatch(0);
            return query.build();

        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }


    /**
     * A method that takes the eventMap and pass it to the buildIndex method then passes the keywords and avail to getQuery
     * function to return a query. Then it does search on the retrieved query and returns the map entries that matched the query.
     * It adds all of them to a map and returns it.
     *
     * @param eventMap
     * @param keywords
     * @param avail
     * @param limit - number of results to be retrieved
     * @return new resulted map from the search
     */
    public SortedMap<Integer, String[]> search(SortedMap<Integer, String []> eventMap, String keywords, int avail, int limit) {
        try {
            buildIndex(eventMap);
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            String field = "name";
            if(keywords.length() == 0){ // if keyword is empty it means the user maybe changed availability
                field = "status";
                keywords = "all";
            }
            Query query = getQuery(field, keywords, avail);
            TopDocs docs = searcher.search(query, limit);
            SortedMap<Integer, String[]> map = new TreeMap<>();
            for (ScoreDoc scoreDoc : docs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                if(doc != null) {
                    int id = Integer.parseInt(doc.get("id"));
                    String[] str = {doc.get("name"), doc.get("userid"), doc.get("avail"), doc.get("purchase")};
                    map.put(id, str);
                }
            }
            return map;
        } catch (Exception e) {
            return null;
        }
    }
}

