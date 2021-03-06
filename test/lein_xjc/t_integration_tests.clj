(ns lein-xjc.t-integration-tests
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [leiningen.install :as install]
            [lein-xjc.plugin :as plugin]
            [leiningen.xjc :as xjc]
            [leiningen.core.project :as project]
            [leiningen.clean :as clean]
            [leiningen.core.user :as user]
            [midje.sweet :refer :all]))

(defn- read-test-project [name]
  "Read one of the test projects under test-projects.
  Copied (and slightly modified) from the leiningen source.
  See leiningen.test.helper."
  (with-redefs [user/profiles (constantly {})]
    (letfn [(read-prj []
              (let [prj (project/read (format
                                        "test-projects/%s/project.clj"
                                        name))]
                (project/init-project
                  (project/project-with-profiles-meta
                    prj (merge @project/default-profiles
                               (:profiles prj))))))
            (clean-prj [prj]
              (clean/clean prj) prj)]
      #(clean-prj (read-prj)))))

(defn- file-exists? [path]
  (let [file (io/file path)]
    (and (.exists file) (.isFile file))))

(defchecker java-sources-created [project class-names]
  (checker [_]
           (let [generated-java (plugin/lein-xjc-src-path project)
                 src-files (map #(format "%s/%s.java"
                                         generated-java
                                         (s/replace % #"\." "/"))
                                class-names)]
             (every? file-exists? src-files))))

(defchecker java-classes-created [project class-names]
  (checker [_]
           (let [classes-dir (format "%s/classes" (:target-path project))
                 class-files (map #(format "%s/%s.class"
                                           classes-dir
                                           (s/replace % #"\." "/"))
                                  class-names)]
             (every? file-exists? class-files))))

(def read-single-xsd-project (read-test-project "single-xsd"))
(def read-mult-xsd-with-bindings-project
  (read-test-project "mult-xsd-with-bindings"))
(def read-project-with-episode (read-test-project "project-with-episode"))

(fact "running 'lein xjc' generates the java sources for
      the simple.xsd schema."
      :integration-test
      (let [project (read-single-xsd-project)]
        (xjc/xjc project) => (java-sources-created
                               project
                               ["com.example.ObjectFactory"
                                "com.example.Something"])))

;; Note: Reading the test projects automatically cleans them. Thus
;; running 'lein install' actually regenerates and compiles the java
;; source files. This behavior is intended.
(fact "running 'lein install' compiles the generated java classes"
      :integration-test
      (let [project (read-single-xsd-project)]
        (install/install project) => (java-classes-created
                                       project
                                       ["com.example.ObjectFactory"
                                        "com.example.Something"])))

(fact "specifying a bindings file via the :bindings keyword makes xjc
      take it into account"
      :integration-test
      (let [project (read-mult-xsd-with-bindings-project)]
        (xjc/xjc project) => (java-sources-created
                               project
                               ["test.first.binding.ObjectFactory"
                                "test.first.binding.FirstXsdType1"
                                "test.second.binding.ObjectFactory"
                                "test.second.binding.SecondXsdType1"])))

(fact "specifying a episode file via the :episode keyword makes xjc take
      it into account. The episode can then be used as a bindings file
      for a sencond xjc call."
      :integration-test
      (let [project (read-project-with-episode)]
        (xjc/xjc project) => (fn
                               [_]
                               (file-exists?
                                 (format "%s/%s"
                                         (:root project)
                                         "target/lein-xjc/episode")))))
