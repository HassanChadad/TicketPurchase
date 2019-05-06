import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.SimpleFSDirectory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SortedMap;
import java.util.TreeMap;

public class Search {

    private static Directory directory;
    private static Analyzer analyzer;

    static void BuildIndex() {
        SortedMap<Integer, String[]> map = new TreeMap<>();
        String[] str = {"Event Test", "1", "0", "4"};
        map.put(1, str);
        String[] str2 = {"Test Event", "2", "10", "10"};
        map.put(2, str2);
        String[] str3 = {"Test Event", "3", "4", "10"};
        map.put(3, str3);
        try {
            directory = new RAMDirectory();
            analyzer = new StandardAnalyzer();
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
            IndexWriter writer = new IndexWriter(directory, indexWriterConfig);
            writer.deleteAll();
            for (int key : map.keySet()) {
                Document document = MapEvents(map.get(key), key);
                writer.addDocument(document);
            }
            writer.close();
        } catch (Exception e) {
            System.out.println(e);
        } finally {
        }
    }

    static Sort GetSort() {
        SortField[] fields = {
                new SortField("name", SortField.Type.STRING), SortField.FIELD_SCORE
        };
        return new Sort(fields); // sort by brand, then by score
    }

    static Document MapEvents(String[] str, int key) {
        Document document = new Document();
        FieldType fieldType = new FieldType();
        fieldType.setTokenized(true);
        fieldType.setStored(true);
        document.add(new TextField("id", key + "", Field.Store.YES));
        document.add(new TextField("name", str[0], Field.Store.YES));
        document.add(new TextField("userid", str[1], Field.Store.YES));
        document.add(new TextField("avail", str[2], Field.Store.YES));
        document.add(new TextField("purchase", str[3], Field.Store.YES));
        document.add(new TextField("status", "all", Field.Store.NO));
        return document;
    }

    static Directory GetDirectory() {
        try {
            Path actual = Paths.get("SampleIndex");  // your output
            return new SimpleFSDirectory(actual);
        } catch (Exception e) {
            return null;
        }
    }

    static Query getQuery(String field, String keywords, int avail) {
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

    public static void main(String[] args) {
        BuildIndex();
        SortedMap<Integer, String[]> map = search("test event", 10);
        for (int key : map.keySet())
            System.out.println(key + "\t" + map.get(key)[0] + ", " + map.get(key)[1] + ", " + map.get(key)[2] + ", " + map.get(key)[3]);
    }

    static SortedMap<Integer, String[]> search(String keywords, int limit) {
        try {
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            String field = "name";
            if(keywords.length() == 0){
                field = "status";
                keywords = "all";
            }
            Query query = getQuery(field, keywords, 1);
            TopDocs docs = searcher.search(query, limit);
            long count = docs.totalHits;
            SortedMap<Integer, String[]> map = new TreeMap<>();
            for (ScoreDoc scoreDoc : docs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                int id = Integer.parseInt(doc.get("id"));
                String[] str = {doc.get("name"), doc.get("userid"), doc.get("avail"), doc.get("purchase")};
                map.put(id, str);
            }
            return map;
        } catch (Exception e) {
            return null;
        }
    }
}

