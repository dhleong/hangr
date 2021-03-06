(ns ^{:author "Daniel Leong"
      :doc "util"}
  hangr.util
  (:require [clojure.string :refer [split]]))

(defn js->real-clj
  "Convenience"
  [js]
  (-> js
      (js->clj :keywordize-keys true)))

(defn id->key
  "Takes a {:chat_id C :gaia_id G} and makes a single keyword"
  [map-id]
  (keyword (str (:chat_id map-id)
                "|"
                (:gaia_id map-id))))

(defn key->id
  "Takes a keyword id and returns the original {:chat_id C :gaia_id G}"
  [map-id]
  (-> map-id
      name
      (split "|")
      (as-> vals
        (zipmap [:chat_id :gaia_id]
                vals))))

(defn join-sorted-by
  "Join sorted sequences by interleaving elements from both
  using the given sort-key"
  [sort-key coll1 coll2 & colls]
  (->> (apply concat coll1 coll2 colls)
       (sort-by sort-key)))

(defn read-package-json
  []
  (if (exists? js/require)
    (js/require (str js/__dirname "/package.json"))
    #js {}))

(defn safe-require
  "Attempt to require something from node, optionally returning
   a default value when not running in node (for example, phantomjs
   cli tests)"
  ([module]
   (safe-require module #js {}))
  ([module default-val]
   (if (exists? js/require)
     (js/require module)
     default-val)))
