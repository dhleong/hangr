(ns ^{:author "Daniel Leong"
      :doc "Widget views"}
  hangr.views.widgets
  (:require [clojure.string :as string]))

;; -- Helper Functions --------------------------------------------------------

(defn avatar-text
  [person]
  (let [name (or (:name person)
                 (:first_name person))]
    (if (and name
             (not= "+" (first name)))
      (->> name
           (#(string/split % #" +"))
           (map first)
           (string/join ""))
      "?")))

;; -- Public widgets ----------------------------------------------------------

(defn avatar
  [user-or-opts & [user]]
  (let [opts (if user
               user-or-opts
               {})
        user (or user user-or-opts)
        avatar-url (:photo_url user)]
    (if avatar-url
      [:img.avatar
       (assoc opts
              :src (str "http:" avatar-url))]
      [:div.avatar
       opts
       (avatar-text user)])))

