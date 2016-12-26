(ns ^{:author "Daniel Leong"
      :doc "db"}
  hangr.db)

(def default-value
  {:connecting? true
   :page [:connecting]
   :people {}
   :self nil
   :convs nil
   :focused? false
   :pending-image nil})
