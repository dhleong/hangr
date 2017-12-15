(ns ^{:author "Daniel Leong"
      :doc "Utilities for creating messages"}
  hangr.util.msg
  (:require [clojure.string :as string :refer [split]]
            [hickory.core :as hickory]
            [url-regex :as original-url-regex]
            #_[hangr.util :refer [safe-require]]))

; url-regex is, by default, a global regex, which, 
;  unlike on jvm, has state. that doesn't play well
;  with clojure regex functions that expct stateless,
;  so we create a new RegExp instance based on the 
;  original, stripping the global flag (but keeping
;  the case insensitivity)
(def url-regex (let [regex-base
                     original-url-regex
                     #_(safe-require
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

(def html-entities
  {"amp" "&"
   "apos" "'"
   "lt" "<"
   "gt" ">"
   "quot" "\""})

(def html-entities-regex
  (re-pattern (str "&("
                   (string/join "|" (keys html-entities))
                   ");")))

(defn parse-string-part
  [part]
  (-> part
      ; TODO &#000; entities?
      (string/replace html-entities-regex (fn [[_ k]]
                                            (get html-entities k)))
      (split-links)))

(defn parse-formatted-part
  [part flags]
  (if (string? part)
    [[:text part flags]]

    (case (first part)
      :b (parse-formatted-part (last part) (assoc flags :bold 1))
      :i (parse-formatted-part (last part) (assoc flags :italic 1))
      :u (parse-formatted-part (last part) (assoc flags :underline 1))

      ;; unexpected?
      (do
        (js/console.warn "UNEXPECTED: " part)
        part))))

(defn parse-html-part
  [part]
  (if (string? part)
    (parse-string-part part)

    (case (first part)
      :br [[:newline]]

      ; TODO: this isn't quite right, but it works okay...
      :div (cons [:newline] (parse-html-part (last part)))

      :b (parse-formatted-part part {})
      :i (parse-formatted-part part {})
      :u (parse-formatted-part part {})

      ; default:
      (cons :text (drop 2 part)))))

(defn html->msg
  [html]
  (->> html
       (hickory/parse-fragment)
       (map hickory/as-hiccup)
       (map parse-html-part)

       ;; clean up excessive newlines (?)
       (reduce
         (fn [result part]
           ; multiple newlines in a row:
           (if (and (= 1 (count part))
                    (= (last result) (first part) [:newline]))
             result
             (concat result part))))

       ; drop any [:newline] at the beginning
       (drop-while #(= :newline (first %)))

       ;; insert a client-generated-id as the first thing
       (cons (client-generated-id))))

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
                   :text (first args)
                   :formatting (when (= 2 (count args))
                                 (second args))}
            :link {:type "LINK"
                   :text (or (second args)
                             (first args))
                   :link_data
                   {:link_target (first args)}}
            :newline {:type "LINE_BREAK"}))
        (rest msg))}}

    :timestamp (* (.now js/Date) 1000)}))
