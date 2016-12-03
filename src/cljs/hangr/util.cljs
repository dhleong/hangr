(ns ^{:author "Daniel Leong"
      :doc "util"}
  hangr.util)

(defn js->real-clj
  "Convenience"
  [js]
  (-> js
      (js->clj :keywordize-keys true)))
