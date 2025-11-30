# GeoNames Data Tools

This folder contains the Python utility and source data used to generate the offline city data that is bundled inside the iOS and Android apps. The goal is to create a fully offline dataset for city search and location selection at runtime, without requiring any network calls.

## Folder Structure

tools/
  geonames/
    cities15000.txt           # Source GeoNames data (tab-delimited)
    build_cities_json.py      # Python conversion script
    README.md                 # This file

## Purpose

The Python script takes the GeoNames file, like `cities15000.txt` and converts it into a compact, single-line JSON file named `cities_offline.dat`. This file is not opened or edited inside Xcode. It is loaded at runtime and parsed by platform code.

## Requirements

Python 3.8 or later is recommended.

Check your Python version:
    python3 --version

No external libraries are required.

## How To Run

From the `tools/geonames/` folder:

Use the default input filename:
    ```python build_cities_json.py```

Or specify a different input filename:
    ```python build_cities_json.py myfile.txt```

This generates:
    cities_offline.dat

This file contains JSON data on a single line.

## Where To Put The Output File

### ANDROID

Place the file here:
    `android/app/src/main/assets/cities_offline.dat`

### IOS

Place the file here:
    `ios/SuntimeAlerts/Resources/cities_offline.dat`

Then in Xcode:
  1. Right-click the Resources group.
  2. Choose "Add Files..."
  3. Ensure the app target is checked.
  4. In the File Inspector, set Type to "Data".
  5. Check that the file is listed in Build Phases -> Copy Bundle Resources.

## Updating The Data

When updating to a new GeoNames dataset:

  1. Download and extract a new `cities15000.zip`.
  2. Replace `cities15000.txt` in this folder.
  3. Run the script again:
         python build_cities_json.py
  4. Replace `cities_offline.dat` in both platforms.
  5. Bump the data version in app code. Example:

     Kotlin:
         const val CITY_DATA_VERSION = 2

     Swift:
         let cityDataVersion = 2

This will trigger a database refresh on next launch.

## Validating The Output

To pretty-print the first 10 records:
    `jq '.[0:10]' cities_offline.dat`

To count total cities:
    `jq length cities_offline.dat`

## Source Data

Official GeoNames exports:
https://www.geonames.org/export/

Recommended dataset:
`cities15000.zip` (cities with population > 15000 and capitals)

## License

GeoNames data is licensed under Creative Commons Attribution 4.0:
https://creativecommons.org/licenses/by/4.0/

Attribution may be required depending on your application.