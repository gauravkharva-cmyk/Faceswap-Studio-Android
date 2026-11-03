# GK Veda Gyan — Offline Sanskrit Corpus
This Android application stores Vedic Sanskrit source material in a local SQLite database.
After installation the app has **no INTERNET permission**, no API calls, and no online account.

Included, subject to source and build-count checks:
- Rigveda Śākala (10 mandalas)
- Śukla Yajurveda Vājasaneyī Mādhyandina (40 adhyayas)
- Sāmaveda Kauthuma (1,875 recited verse entries)
- Atharvaveda Śaunaka (20 kāṇḍas)

These are one selected recension from each Veda, **not every surviving śākhā**.
A Hindi meaning and individual detailed commentary are **not verified for most verses**;
the reading interface explicitly reports that omission, not an invented translation.
The number of text records may differ from conventional verse totals because the
source dataset sometimes treats an unnumbered tail as a separate record. Build
reports counts and checks for unexpectedly missing sections.

Upstream credits:
- Rigveda, Yajurveda and Atharvaveda: bhavykhatri/DharmicData, ODbL database;
  sourced from the Vedic Heritage Portal. Check individual original text rights.
- Sāmaveda Kauthuma original encoded by Anshuman Pandey,
  https://sanskritdocuments.org/doc_veda/sv-kauthuma.html.
  SanskritDocuments conditions restrict reuse for promotion/commercial distribution.
  This APK is prepared solely for personal offline research/study, with attribution.
- Sample Hindi summaries are newly authored, explicitly non-authoritative.

User-visible credit: Created By : Gaurav Kharva.
Use a connected Android developer environment or GitHub Actions to build and sign
an installable debug APK. Internet is used at BUILD TIME ONLY.
