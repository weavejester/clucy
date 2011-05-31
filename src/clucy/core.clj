(ns clucy.core
  (:require [clojure.java.io :as io])
  (:import java.io.File
           java.io.StringReader
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
           org.apache.lucene.search.highlight.QueryScorer
           org.apache.lucene.search.highlight.Highlighter
           org.apache.lucene.search.highlight.SimpleHTMLFormatter
           org.apache.lucene.index.Term
           org.apache.lucene.search.TermQuery))

(def *version*  Version/LUCENE_30)
(def *analyzer* (StandardAnalyzer. *version*))
(def *optimize-frequency* 1)

(defn as-str [x]
  (if (keyword? x)
    (name x)
    (str x)))

(defstruct
    #^{:doc "Structure for clucy indexes."}
    clucy-index :index :optimize-frequency :updates)

;; flag to indicate a default "_content" field should be maintained
(def *content* true)

(defn memory-index
  "Create a new index in RAM."
  []
  (atom (struct-map clucy-index
          :index (RAMDirectory.)
          :optimize-frequency *optimize-frequency*
          :updates 0)))

(defn disk-index
  "Create a new index in a directory on disk."
  [dir-path]
  (atom (struct-map clucy-index
          :index (NIOFSDirectory. (io/file dir-path))
          :optimize-frequency *optimize-frequency*
          :updates 0)))

(defn- index-writer
  "Create an IndexWriter."
  [index]
  (IndexWriter. (:index @index)
                *analyzer*
                IndexWriter$MaxFieldLength/UNLIMITED))

(defn- optimize-index
  "Optimized the provided index if the number of updates matches or
  exceeds the optimize frequency."
  [index]
  (if (<= (:optimize-frequency @index) (:updates @index))
    (with-open [writer (index-writer index)]
      (.optimize writer)
      (swap! index assoc :updates 0))
    index))

(defn- add-field
  "Add a Field to a Document."
  ([document key value]
     (add-field document key value {}))

  ([document key value meta-map]
       (.add document
             (Field. (as-str key) (as-str value)
                     (if (and meta-map (= false (:stored meta-map)))
                       Field$Store/NO
                       Field$Store/YES)
                     (if (and meta-map (= false (:indexed meta-map)))
                       Field$Index/NO
                       Field$Index/ANALYZED)))))

(defn- map-stored
  "Returns a hash-map containing all of the values in the map that
  will be stored in the search index."
  [map-in]
  (merge {}
         (filter (complement nil?)
                 (map (fn [item]
                        (if (or (= nil (meta map-in))
                                (not= false
                                      (:stored ((first item) (meta map-in)))))
                          item)) map-in))))

(defn- concat-values
  "Concatenate all the maps values being stored into a single string."
  [map-in]
  (apply str (interpose " " (vals (map-stored map-in)))))

(defn- map->document
  "Create a Document from a map."
  [map]
  (let [document (Document.)]
    (doseq [[key value] map]
      (add-field document key value (key (meta map))))
    (if *content*
      (add-field document :_content (concat-values map)))
    document))

(defn add
  "Add hash-maps to the search index."
  [index & maps]
  (with-open [writer (index-writer index)]
    (doseq [m maps]
      (swap! index assoc :updates (inc (:updates @index)))
      (.addDocument writer (map->document m))))
  (optimize-index index))

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
        (.deleteDocuments writer query))
      (swap! index assoc :updates (inc (:updates @index)))))
  (optimize-index index))

(defn- document->map
  "Turn a Document object into a map."
  ([document score]
     (document->map document score (constantly nil)))
  ([document score highlighter]
     (let [m (-> (into {}
                   (for [f (.getFields document)]
                     [(keyword (.name f)) (.stringValue f)])))
           fragments (highlighter m) ; so that we can highlight :_content
           m (dissoc m :_content)]
       (with-meta
         m
         (-> (into {}
                   (for [f (.getFields document)]
                     [(keyword (.name f))
                      {:indexed (.isIndexed f)
                       :stored (.isStored f)
                       :tokenized (.isTokenized f)}]))
             (assoc :_fragments fragments)
             (assoc :_score score)
             (dissoc :_content))))))

(defn- make-highlighter
  "Create a highlighter function which will take a map and return highlighted
fragments."
  [query searcher config]
  (if config
    (let [indexReader (.getIndexReader searcher)
          scorer (QueryScorer. (.rewrite query indexReader))
          config (merge {:field :_content
                         :max-fragments 5
                         :separator "..."
                         :pre "<b>"
                         :post "</b>"}
                        config)
          {:keys [field max-fragments separator fragments-key pre post]} config
          highlighter (Highlighter. (SimpleHTMLFormatter. pre post) scorer)]
      (fn [m]
        (let [str (field m)
              token-stream (.tokenStream *analyzer*
                                         (name field)
                                         (StringReader. str))]
          (.getBestFragments highlighter
                             token-stream
                             str
                             max-fragments
                             separator))))
    (constantly nil)))

(defn search
  "Search the supplied index with a query string."
  [index query max-results & {:keys [highlight default-field]}]
  (if (every? false? [default-field *content*])
    (throw (Exception. "No default search field specified"))
    (let [default-field (or default-field :_content)]
      (with-open [searcher (IndexSearcher. (:index @index))]
        (let [parser (QueryParser. *version* (as-str default-field) *analyzer*)
              query  (.parse parser query)
              hits   (.search searcher query max-results)
              highlighter (make-highlighter query searcher highlight)]
          (doall
           (for [hit (.scoreDocs hits)]
             (document->map (.doc searcher (.doc hit))
                            (.score hit)
                            highlighter))))))))

(defn search-and-delete
  "Search the supplied index with a query string and then delete all
of the results."
  ([index query]
     (if *content*
       (search-and-delete index query :_content)
       (throw (Exception. "No default search field specified"))))
  ([index query default-field]
    (with-open [writer (index-writer index)]
      (let [parser (QueryParser. *version* (as-str default-field) *analyzer*)
            query  (.parse parser query)]
        (.deleteDocuments writer query)
        (swap! index assoc :updates (inc (:updates @index)))))
    (optimize-index index)))
