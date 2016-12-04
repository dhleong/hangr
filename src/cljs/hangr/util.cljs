(ns ^{:author "Daniel Leong"
      :doc "util"}
  hangr.util)

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
      (clojure.string/split "|")
      (as-> vals
        (zipmap [:chat_id :gaia_id]
                vals))))

