(defproject clucy "0.3.0"
  :description "A Clojure interface to the Lucene search engine"
  :url "http://github/weavejester/clucy"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.apache.lucene/lucene-core "3.5.0"]
                 [org.apache.lucene/lucene-highlighter "3.5.0"]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dev-dependencies [[lein-multi "1.1.0"]]
  :multi-deps {"1.2-lucene2" [[org.clojure/clojure "1.2.1"]
                              [org.apache.lucene/lucene-core "2.9.2"]
                              [org.apache.lucene/lucene-highlighter "2.9.2"]]
               "1.3-lucene2" [[org.clojure/clojure "1.3.0"]
                              [org.apache.lucene/lucene-core "2.9.2"]
                              [org.apache.lucene/lucene-highlighter "2.9.2"]]
               "1.2-lucene3" [[org.clojure/clojure "1.2.1"]
                              [org.apache.lucene/lucene-core "3.5.0"]
                              [org.apache.lucene/lucene-highlighter "3.5.0"]]})
