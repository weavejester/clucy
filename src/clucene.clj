(ns clucene
  (:use clojure.contrib.java-utils)
  (:import org.apache.lucene.document.Document)
  (:import (org.apache.lucene.document Field, Field$Store, Field$Index))
  (:import (org.apache.lucene.index IndexWriter, IndexWriter$MaxFieldLength))
  (:import org.apache.lucene.analysis.standard.StandardAnalyzer)
  (:import org.apache.lucene.queryParser.QueryParser)
  (:import org.apache.lucene.search.IndexSearcher)
  (:import org.apache.lucene.store.RAMDirectory)
  (:import org.apache.lucene.util.Version))

(def *version*  Version/LUCENE_30)
(def *analyzer* (StandardAnalyzer. *version*))

(defn memory-index
  "Create a new index in RAM."
  []
  (RAMDirectory.))

(defn- index-writer
  "Create an IndexWriter."
  [index]
  (IndexWriter. index *analyzer* true IndexWriter$MaxFieldLength/UNLIMITED))

(defn- add-field
  "Add a Field to a Document."
  [document key value]
  (.add document
    (Field. (as-str key) (as-str value)
            Field$Store/YES
            Field$Index/ANALYZED)))

(defn- map->document
  "Create a Document from a map."
  [map]
  (let [document (Document.)]
    (doseq [[key value] map]
      (add-field document key value))
    document))

(defn add
  "Add hash-maps to the search index"
  [index & maps]
  (with-open [writer (index-writer index)]
    (doseq [m maps]
      (.addDocument writer (map->document m)))))

(defn- document->map
  "Turn a Document object into a map."
  [document]
  (into {}
    (for [f (.getFields document)]
      [(keyword (.name f)) (.stringValue f)])))

(defn search
  "Search the supplied index with a query string."
  [index query max-results default-field]
  (with-open [searcher (IndexSearcher. index)]
    (let [parser (QueryParser. *version* (as-str default-field) *analyzer*)
          query  (.parse parser query)
          hits   (.search searcher query max-results)]
      (doall
        (for [hit (.scoreDocs hits)]
          (document->map (.doc searcher (.doc hit))))))))
