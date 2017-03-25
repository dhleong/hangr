(ns ^{:author "Daniel Leong"
      :doc "Simple, non-comprehensive Markdown utils"}
  hangr.util.ui.markdown
  (:require [clojure.string :as str]))

(def ul-regex #"^[ ]*-[ ]*(.*)$")
(def ol-regex #"^[ ]*[0-9]\.[ ]*(.*)$")
(def b-regex #"^(.*)\*\*(.*)\*\*(.*)$")
(def issue-regex #"^(.*)#([0-9]+)(.*)$")

(defn- apply-update
  [opts element]
  (let [el-type (first element)]
    (if-let [update-fn (get-in opts [:update el-type])]
      (update-fn element)
      element)))

(defn- maybe-span
  "If there's just one part, return it
  directly; if there are more, wrap them
  in a :span"
  [& parts]
  (let [parts (remove empty? parts)]
    (case (count parts)
      0 nil
      1 (first parts)
      (vec
        (cons :span
              parts)))))

(defn- collapse-into
  "If `parts` is a container like :span, collapse
  its contents into `spec`. Otherwise, just create
  a normal vector of [spec parts]"
  [spec parts]
  (if (= :span (first parts))
    (assoc parts 0 spec)
    [spec parts]))

(defn- collapse-list-type
  [result item container-spec]
  {:pre [(or (= :ul container-spec)
             (= :ol container-spec))]}
  (if (= container-spec (-> result last first))
    ; join with previous
    (let [ul-items (-> result last rest)]
      (concat (drop-last result)
              [(vec
                 (concat [container-spec]
                         ul-items
                         [item]))]))
    ; create a new ul to contain it
    (concat result [[container-spec item]])))

(defn- collapse-lists
  [result item]
  (cond
    (= :li.ul (first item))
    (collapse-list-type result item :ul)
    (= :li.ol (first item))
    (collapse-list-type result item :ol)
    ; otherwise, let it be
    :else (concat result [item])))

(defn- parse-github
  [opts line]
  (when-let [repo-url (:github opts)]
    (when-let [[_ before issue after] (re-find issue-regex line)]
      [before
       (apply-update
         opts
         [:a {:href (str repo-url
                         "/issues/"
                         issue)}
          (str "#" issue)])
       after])))

(defn- wrap-line
  [opts line]
  ; a cond-let macro would be wonderful...
  (if-let [li (re-find ul-regex line)]
    (collapse-into :li.ul (wrap-line opts (second li)))
    (if-let [li (re-find ol-regex line)]
      (collapse-into :li.ol (wrap-line opts (second li)))
      (if-let [[_ before b after] (re-find b-regex line)]
        (maybe-span
          before
          (apply-update opts [:b b])
          after)
        (if-let [[before github after]
                 (parse-github opts line)]
          (maybe-span before github after)
          ; nothing left to do:
          line)))))

(defn markdown->hiccup
  "Opts is an optional map with the following possible keys:
  :github - Url to a github repository for #issue parsing
  :update - A map of :element -> fn, where `fn` will be called
      for each type of element"
  ([container-spec markdown]
   (markdown->hiccup
     container-spec markdown {}))
  ([container-spec markdown opts]
   (vec
     (cons
       container-spec
       (let [lines (str/split markdown "\n")]
         (->> lines
              (map (partial wrap-line opts))
              (reduce collapse-lists [])
              vec))))))

