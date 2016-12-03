(ns ^{:author "Daniel Leong"
      :doc "db"}
  hangr.db)

(def default-value
  {:loading? true
   :page [:connecting]
   :people {}
   :convs {}})
