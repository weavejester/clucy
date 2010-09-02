(ns clucy.core
  (:use clojure.contrib.java-utils)
  (:import java.io.File
           org.apache.lucene.document.Document
           (org.apache.lucene.document Field Field$Store Field$Index)
           (org.apache.lucene.index IndexWriter IndexWriter$MaxFieldLength)
           org.apache.lucene.analysis.standard.StandardAnalyzer
           org.apache.lucene.queryParser.QueryParser
           org.apache.lucene.search.IndexSearcher
           (org.apache.lucene.store RAMDirectory NIOFSDirectory)
           org.apache.lucene.util.Version
           org.apache.lucene.search.BooleanQuery
           org.apache.lucene.search.BooleanClause
           org.apache.lucene.search.BooleanClause$Occur
           org.apache.lucene.index.Term
           org.apache.lucene.search.TermQuery))

(def *version*  Version/LUCENE_30)
(def *analyzer* (StandardAnalyzer. *version*))

(defn memory-index
  "Create a new index in RAM."
  []
  (RAMDirectory.))

(defn disk-index
  "Create a new index in a directory on disk."
  [dir-path]
  (NIOFSDirectory. (File. dir-path)))

(defn- index-writer
  "Create an IndexWriter."
  [index]
  (IndexWriter. index *analyzer* IndexWriter$MaxFieldLength/UNLIMITED))

(defn- add-field
  "Add a Field to a Document."
  [document key value]
  (.add document
    (Field. (as-str key) (as-str value)
            Field$Store/YES
            Field$Index/ANALYZED)))

(defn- concat-values
  "Concatenate all the maps values into a single string."
  [map]
  (apply str (interpose " " (vals map))))

(defn- map->document
  "Create a Document from a map."
  [map]
  (let [document (Document.)]
    (doseq [[key value] map]
      (add-field document key value))
    (add-field document :_content (concat-values map))
    document))

(defn add
  "Add hash-maps to the search index."
  [index & maps]
  (with-open [writer (index-writer index)]
    (doseq [m maps]
      (.addDocument writer (map->document m)))
    (.optimize writer)))

(defn delete
  "Deletes hash-maps from the search index."
  [index & maps]
  (with-open [writer (index-writer index)]
    (doseq [m maps]
      (let [query (BooleanQuery.)]
        (doseq [[key value] m]
          (.add query
                (BooleanClause.
                 (TermQuery. (Term. (.toLowerCase (as-str key))
                                    (.toLowerCase (as-str value))))
                 BooleanClause$Occur/MUST)))
        (.deleteDocuments writer query)))
    (.optimize writer)))

(defn- document->map
  "Turn a Document object into a map."
  [document]
  (-> (into {}
        (for [f (.getFields document)]
          [(keyword (.name f)) (.stringValue f)]))
      (dissoc :_content)))

(defn search
  "Search the supplied index with a query string."
  ([index query max-results]
    (search index query max-results :_content))
  ([index query max-results default-field]
    (with-open [searcher (IndexSearcher. index)]
      (let [parser (QueryParser. *version* (as-str default-field) *analyzer*)
            query  (.parse parser query)
            hits   (.search searcher query max-results)]
        (doall
          (for [hit (.scoreDocs hits)]
            (document->map (.doc searcher (.doc hit)))))))))

(defn search-and-delete
  "Search the supplied index with a query string and then delete all
of the results."
  ([index query max-results]
    (search-and-delete index query max-results :_content))
  ([index query max-results default-field]
    (with-open [writer (index-writer index)]
      (let [parser (QueryParser. *version* (as-str default-field) *analyzer*)
            query  (.parse parser query)]
        (.deleteDocuments writer query))
      (.optimize writer))))
