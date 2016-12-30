(ns ^{:author "Daniel Leong"
      :doc "People utils"}
  hangr.util.people)

(defn first-name
  [person]
  (or (:first_name person)
      ; TODO try to split off the first name
      (:name person)))
