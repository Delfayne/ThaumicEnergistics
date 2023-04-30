# 2023/04/30 - 2.2.7
## Misc
* Building off AE2 Extended Life now rather than base AE2
* Converted changelog to markdown
* Relocated primary git branch to main
* Artifact name changed to reflect extended life

# 2023/03/06 - 2.2.6
* Creative Essentia Cells now list MAX_INT essentia instead of 1000. They were previously still infinite, but with
  a cap of 1000 present in the AE system would prohibit large crafts.

# 2022/07/09 - 2.2.5 Release
* Corrected Essentia cell partitioning. Previously cells could be partitioned, but this was being ignored. Now
  partitioned cells will only accept the filtered essentia.
* Improved net handling code around Arcane Crafting Terminal to handle diffs of the AE2 inventory changes rather than
  the entire inventory. Should be a bit faster.
* Fixing Essentia export buses not respecting export cards. This continued to work on the first aspect in the list,
  without checking if it was needed or not. Now this will pass to the next essentia if none would be transferred.

## Build
* Fixed a number of issues around the use of the CurseMaven plugin, probably fallout from the CurseForge API
  changes lately. Builds simply broke with missing dependencies, and some version numbers for dependent mods seemed
  shifted.

# 2019/07/28 - 2.2.3 - Release
* You can now scan any item or block from AE2 to unlock research
* Added ME Controller as a Research Aid
* Added Buttons & Scroll to Essentia Terminal
* Hide Dummy Aspect (#381)
* Update ru_ru.lang (#376)

# 2019/05/01 - 2.2.2 - Release
* Compress JEI Packet (#368)
* Fixed being able to autocraft any item
* Fixed Autocraft Autostart bug (#373)
* Fixed firing crafting events (#369)
* Fixed crafting non-1 stacks (#375)

# 2019/03/12 - 2.2.1 - Release
* Fixed issue with inserting non-phial items into Essentia Terminal (#366)
* Fixed Tall Terminal Style (#365)
* Minor Research Improvements

# 2019/03/05 - 2.2.0 - Beta
* Implemented Autocrafting for the Arcane Crafting Terminal
* Added Terminal Style option to ACT
* Fixed issue with Infusion Provider not always connecting
* Fixed issue with parts not dropping contents
* Fixes issue when wrenching parts and gui still opens

# 2019/02/07 - 2.1.1 - Release
* Added ru_ru.lang
* Fixed issue with viewing just crafted/stored items

# 2019/01/05 - 2.1.0 - Beta
* Added ability to search items by the aspects they break into
* Added JEI & Thaumic JEI integration
* Added Clear Grid button to ACT
* Fixed issue with dragging stacks in ACT
* Minor bug fixes

# 2018/11/28 - 2.0.0 - Beta
* Initial public build for Minecraft 1.12.2
* Rewritten from scratch
* Added Essentia Storage Cells
* Added Essentia Import Bus
* Added Essentia Export Bus
* Added Essentia Storage Bus
* Added Essentia Terminal
* Added Infusion Provider
* Added Arcane Crafting Terminal
