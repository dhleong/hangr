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
              :src (str (when-not (string/starts-with? avatar-url "http") 
                          "http:")
                        avatar-url))]
      [:div.avatar
       opts
       (avatar-text user)])))

(defn icon
  "A material design icon. `spec` is a keyword
  that is usually the name of the icon, but
  can also have .class like normal hiccup"
  [spec]
  (let [spec (name spec)
        class-offset (.indexOf spec ".")
        classes (when (not= -1 class-offset)
                  (subs spec class-offset))
        icon-name (if (not= -1 class-offset)
                    (subs spec 0 class-offset)
                    spec)]
    [(keyword (str "i.material-icons" classes)) icon-name]))

(defn typing-indicator
  [state]
  [:div.typing-indicator
   {:class (name state)}
   [:span.part]
   [:span.part]
   [:span.part]])
