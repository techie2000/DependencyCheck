How does dependency-check work?
===========
Dependency-check works by collecting information about the files it scans (using Analyzers). The information collected
is called Evidence; there are three types of evidence collected: vendor, product, and version. For instance, the
JarAnalyzer will collect information from the Manifest, pom.xml, and the package names within the JAR files scanned and
it has heuristics to place the information from the various sources into one or more buckets of evidence.

Within the NVD API CVE Data (schema can be found [here](https://csrc.nist.gov/schema/nvd/api/2.0/cve_api_json_2.0.schema)) each CVE Entry has
a list of vulnerable software:

```json
"cve": {
    "id": "CVE-2008-3909",

    ...

    "cpeMatch": [
      {
        "vulnerable": true,
        "criteria": "cpe:2.3:a:django_project:django:0.91:*:*:*:*:*:*:*",
        "matchCriteriaId": "B1CE5394-8883-47DB-9214-CCDD05811179"
      },
      {
        "vulnerable": true,
        "criteria": "cpe:2.3:a:django_project:django:0.95:*:*:*:*:*:*:*",
        "matchCriteriaId": "1D617048-648D-4EA1-A779-F6B157AB641E"
      },
      {
        "vulnerable": true,
        "criteria": "cpe:2.3:a:django_project:django:0.96:*:*:*:*:*:*:*",
        "matchCriteriaId": "3F54F75F-B2BC-4A44-B93B-DB75856BEC45"
      }
    ]

    ...
}
```

These CPE entries are read "cpe:/[Entry Type]:[Vendor]:[Product]:[Version]:[Revision]:...". The CPE data is collected
and stored in a [Lucene Index](http://lucene.apache.org/). Dependency-check then use the Evidence collected and attempt
to match an entry from the Lucene CPE Index. If found, the CPEAnalyzer will add an Identifier to the Dependency and
subsequently to the report. Once a CPE has been identified the associated CVE entries are added to the report.

One important point about the evidence is that it is rated using different confidence levels - low, medium, high, and
highest. These confidence levels are applied to each item of evidence. When the CPE is determined it is given a confidence
level that is equal to the lowest level confidence level of evidence used during identification. If only highest confidence
evidence was used in determining the CPE then the CPE would have a highest confidence level.

Because of the way dependency-check works both false positives and false negatives may exist. Please read
[How to read the report](thereport.html) to get a better understanding of sorting through the false positives and false
negatives.

Dependency-check does not currently use file hashes for identification. If the dependency was built from source the hash
likely will not match the "published" hash. While the evidence based mechanism currently used can also be unreliable the
design decision was to avoid maintaining a hash database of known vulnerable libraries. A future enhancement may add some
hash matching for very common well known libraries (Spring, Struts, etc.).