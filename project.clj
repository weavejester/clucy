(defproject clucy "0.2.3"
  :description "A Clojure interface to the Lucene search engine"
  :url "http://github/weavejester/clucy"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.apache.lucene/lucene-core "3.0.3"]
                 [org.apache.lucene/lucene-highlighter "3.0.3"]]
  :dev-dependencies [[lein-multi "1.0.0"]]
  :multi-deps {"lucene2" [[org.clojure/clojure "1.2.1"]
                          [org.apache.lucene/lucene-core "2.9.2"]
                          [org.apache.lucene/lucene-highlighter "2.9.2"]]})
