(ns build
  (:require [babashka.http-server :as http-server]
            [babashka.curl :as curl]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [markdown.core :as md]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [pod.babashka.fswatcher :as fw]
            [camel-snake-kebab.core :as csk])

  (:import (java.time LocalDateTime)))

(defn- start-server!
  []
  (http-server/exec {:port 1337 :dir "./outputs"}))

(def DOCS_ROOT (fs/path (fs/cwd) "docs"))
(def OUTPUTS_ROOT (fs/path (fs/cwd) "outputs"))

(when-not (fs/exists? OUTPUTS_ROOT)
  (fs/create-dir OUTPUTS_ROOT))

(defn- resolve-docs-file-or-dirs!
  ([filepath] (resolve-docs-file-or-dirs! filepath false))
  ([filepath create-if-not-exist?]
   (let [f    (fs/real-path
               (if (string? filepath)
                 (fs/file (fs/path DOCS_ROOT filepath)) filepath))
         edn? (string/ends-with? f ".edn")
         md?  (string/ends-with? f ".md")]
     (when (and (not (fs/exists? f))
                (true? create-if-not-exist?))
       (if (fs/extension f)
         (fs/create-file f)
         (fs/create-dir f)))

     (let [dir? (fs/directory? f)]
       (cond-> (if dir? f (slurp (fs/read-all-bytes f)))
         edn?
         (edn/read-string)

         md?
         (md/md-to-html-string)

         dir?
         (fs/list-dir))))))

(defn- parse-md-assets!
  [content assets-fn!]
  (if-let [matched (re-seq #"src\s*=\s*\"([^\"]+)\"" content)]
    (do
      (assets-fn! (map second matched))
      (reduce #(string/replace %1 (first %2) (string/replace (first %2) "/" "_")) content matched))
    content))

(defn- parse-assets-from-a-category!
  [category file]
  (let [relative-root (fs/parent file)
        assets-fn!    (fn [assets]
                        (prn "[Handle assets]" assets)
                        (-> assets
                            identity))]
    (cond-> category

      (not (string/blank? (:content category)))
      (assoc :content (parse-md-assets! (:content category) assets-fn!))

      (seq (:demos category))
      (assoc :demos (assets-fn! (:demos category))))))

(defn- ensure-output-assets-dir!
  []
  (let [f (fs/path OUTPUTS_ROOT "assets")]
    (when-not (fs/exists? f)
      (fs/create-dir f)) f))

(defn- build-assets!
  [categories]
  (when-let [categories (seq categories)]
    (doseq [f categories
            :let [f' (fs/path f "assets")]]
      (when-let [f'' (and (fs/directory? f') (ensure-output-assets-dir!))]
        (println "D:found-assets:" f')
        (fs/copy-tree f' f'' {:replace-existing true})))))

(defn- build-docs!
  ([] (build-docs! false true))
  ([dev-mode? build-assets?]
   (let [output     (resolve-docs-file-or-dirs! "./config.edn")
         categories (sort (filterv fs/directory? (resolve-docs-file-or-dirs! ".")))]
     (let [results (->> categories
                        (map (fn [f] (let [items (filterv #(string/ends-with? (.toString %) ".edn")
                                                          (resolve-docs-file-or-dirs! f))]
                                       ;; build a category
                                       (let [category-fn? (fn [pred] #(filterv (fn [f] (pred (fs/file-name f) "config.edn")) %))
                                             [category items'] ((juxt (category-fn? =) (category-fn? not=)) items)]
                                         (when-let [category (or (some-> (first category)
                                                                         (resolve-docs-file-or-dirs!))
                                                                 {:title (fs/file-name f)})]
                                           (let [category-k (csk/->snake_case_string
                                                             (string/lower-case (fs/file-name f)))
                                                 category   (assoc category :key category-k)]

                                             (->> items'
                                                  (map #(let [config       (resolve-docs-file-or-dirs! %)
                                                              content-file (fs/file (string/replace-first (.toString %) #".edn$" ".md"))]
                                                          (-> (cond-> config

                                                                (and (nil? (:content config))
                                                                     (fs/exists? content-file))
                                                                (assoc :content (resolve-docs-file-or-dirs! content-file)))
                                                              (assoc :key (-> (str category-k "/" (fs/file-name (fs/strip-ext content-file)))
                                                                              (string/lower-case)
                                                                              (csk/->snake_case_string))))))

                                                  (assoc category :children))))))))
                        (assoc output :children))
           results (assoc results :version (.toString (LocalDateTime/now)))]

       ;; save assets
       (when build-assets?
         (build-assets! categories))

       ;; save outputs
       (spit (fs/file (fs/path OUTPUTS_ROOT "handbooks.edn")) (pr-str results))

       (when-not dev-mode?
         (spit (fs/file (fs/path OUTPUTS_ROOT "handbooks.json")) (json/generate-string results)))

       results))))

(defn start-dev-mode!
  []

  (println "[Start watcher]")
  (let [build! #(time
                 (let [build-assets? (or (= :init %)
                                         (string/includes? % "/assets"))]
                   (when-let [ret (build-docs! true build-assets?)]
                     (println "[Build] " (:version ret) (str %)))))]
    ;; watch docs files changes
    (fw/watch (.toString (fs/real-path DOCS_ROOT))
              #(let [changed-file (:path %)]
                 ;; filter buffer files
                 (when (and changed-file (not (string/ends-with? changed-file "~")))
                   (build! changed-file)))
              {:recursive true
               :delay-ms  500})
    (build! :init))

  (println "[Start server]")
  (start-server!))

(defn start-prod-mode!
  []
  (time
   (let [ret (build-docs!)]
     (println "Docs version:" (:version ret)))))

(comment
  (curl/head "http://localhost:1337/handbooks.edn")
  (curl/get "http://localhost:1337/handbooks.edn"))
