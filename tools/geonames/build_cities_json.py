import json
import sys

INPUT_FILE = "cities15000.txt"   # default input, from cities15000.zip
OUTPUT_FILE = "cities_offline.dat"


def main():
    # Allow optional input filename as first CLI argument; fall back to default.
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    else:
        input_file = INPUT_FILE

    cities = []

    with open(input_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue

            parts = line.split("\t")
            if len(parts) < 19:
                # malformed line, skip
                continue

            (
                geonameid,        # 0
                name,             # 1
                asciiname,        # 2
                alternatenames,   # 3
                latitude,         # 4
                longitude,        # 5
                feature_class,    # 6
                feature_code,     # 7
                country_code,     # 8
                cc2,              # 9
                admin1_code,      # 10
                admin2_code,      # 11
                admin3_code,      # 12
                admin4_code,      # 13
                population,       # 14
                elevation,        # 15
                dem,              # 16
                timezone,         # 17
                modification_date # 18
            ) = parts[:19]

            # cities15000 already has cities, but we can be explicit:
            if feature_class != "P":
                continue  # keep only populated places

            try:
                lat = float(latitude)
                lon = float(longitude)
            except ValueError:
                continue

            try:
                pop = int(population)
            except ValueError:
                pop = 0

            city = {
                "id": int(geonameid),
                "name": name,
                "asciiName": asciiname,
                "countryCode": country_code,
                "admin1Code": admin1_code,
                "lat": lat,
                "lon": lon,
                "timezone": timezone,
                "population": pop,
            }

            cities.append(city)

    # Write compact JSON (no pretty printing to keep size small).
    # Set ensure_ascii=False so Unicode city names are preserved.
    with open(OUTPUT_FILE, "w", encoding="utf-8") as out:
        json.dump(cities, out, ensure_ascii=False, separators=(",", ":"))

    print(f"Wrote {len(cities)} cities to {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
