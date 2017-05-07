(ns ^{:author "Daniel Leong"
      :doc "Utilities for creating messages"}
  hangr.util.msg
  (:require [clojure.string :as string :refer [split]]
            [hangr.util :refer [safe-require]]))

; url-regex is, by default, a global regex, which, 
;  unlike on jvm, has state. that doesn't play well
;  with clojure regex functions that expct stateless,
;  so we create a new RegExp instance based on the 
;  original, stripping the global flag (but keeping
;  the case insensitivity)
(def url-regex (let [regex-base
                     (safe-require
                       "url-regex"

                       ; only for phantomjs tests; we use node tests
                       ; to actually test the msg utils:
                       (constantly #"\b(www\..*\.com)\b"))]
                 (js/RegExp. (.-source (regex-base)) "i")))

(defn- split-links
  [text]
  (let [regex nil]
    (loop [links (re-seq url-regex text)
          text text
          result []]
     (if-let [link (first links)]
       (let [before (subs text 
                          0
                          (.indexOf text link))]
         (recur
           (next links)
           (subs text (+ (.-length before)
                         (.-length link)))
           (concat
             result
             (if (> (.-length before) 0)
               [[:text before]
                [:link link]]
               [[:link link]]))))
       (if (> (.-length text) 0)
         (concat
           result
           [[:text text]])
         result)))))

(defn client-generated-id
  []
  ;; this is what hangupsjs does if you don't supply one:
  (str
    (.round js/Math
            (* (.random js/Math)
               (.pow js/Math 2, 32)))))

(defn html->msg
  [html]
  (let [lines (-> html
                  ; TODO any other entities?
                  (string/replace "&nbsp;" " ")
                  (split #"(?:\<br[ /]*>)+"))]
    ;; TODO formatting? <a>-style links?
    ;; TODO images?
    ;; TODO message type? (eg /me)
    (->> lines
         (mapcat
           (fn [line]
             (concat
               (split-links line)
               [[:newline]])))
         drop-last
         ;; insert a client-generated-id as the first thing
         (cons (client-generated-id)))))

(defn msg->event
  ([msg]
   (msg->event msg nil))
  ([msg image-path]
   {:id (first msg)
    :client-generated-id (first msg)
    :chat_message
    {:message_content
     {:attachment
      (when image-path
        [{:embed_item
          {:embeds.PlusPhoto.plus_photo
           {:url image-path
            :thumbnail
            {:url image-path
             :image_url image-path}}}}])
      :segment
      (map
        (fn [[type & args]]
          (case (keyword type)
            :text {:type "TEXT"
                   :text (first args)}
            :link {:type "LINK"
                   :text (or (second args)
                             (first args))
                   :link_data
                   {:link_target (first args)}}
            :newline {:type "NEWLINE"}))
        (rest msg))}}
    :timestamp (* (.now js/Date) 1000)}))
