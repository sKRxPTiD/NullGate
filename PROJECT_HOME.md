# Canonical project home

All NullGate project material is consolidated under:

`/mnt/TerraDrive/Null Protocol/Apps/NullGate`

The active Git repository is the `source/` directory. Releases, historical
prototype material, artwork and private deployment artifacts are retained in
their named sibling directories. DoloWOLF's installed launcher points back to
this canonical source tree.

TerraDrive is mounted through NTFS/FUSE and does not enforce private Unix file
modes. Encrypted signing keystores may be retained with the project, but their
plaintext password files must remain in DoloWOLF's protected local credential
directory and be supplied with `NULLGATE_KEYPASS_FILE` when building.
