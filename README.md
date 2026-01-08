<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/MagmaFlow_header.png" alt="MagmaFlow Header" width="1000"/>
</p>

<h1 align="center">MagmaFlow</h1>

<h3 align="center"><i>An Interactive Volcano Plot Application for Differential Gene Expression Analysis</i></h3>

<p align="center">
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Java-17+-orange.svg" alt="Java"></a>
  <a href="https://openjfx.io/"><img src="https://img.shields.io/badge/JavaFX-17+-blue.svg" alt="JavaFX"></a>
  <a href="#license"><img src="https://img.shields.io/badge/License-Non--Commercial%20EULA-red.svg" alt="License"></a>
  <a href="https://zenodo.org/records/17011629"><img src="https://img.shields.io/badge/DOI-10.5281%2Fzenodo.17011629-blue.svg" alt="DOI"></a>
</p>

---

## Overview

**MagmaFlow** is a powerful, user-friendly JavaFX application designed for creating interactive volcano plots from differential gene expression data. It provides researchers with an intuitive interface to visualize statistical significance versus fold change patterns in genomic datasets with **publication-ready quality**.

---

## Key Features

### Interactive Visualization

| Feature | Description |
|:--------|:------------|
| **Double-Click Gene Selection** | Instantly add/remove target genes by double-clicking data points |
| **Drag & Drop Labels** | Reposition gene annotations by dragging them anywhere |
| **Smart Edge Connections** | Choose how annotation lines connect (left, right, center, smart, auto) |
| **Advanced Zoom & Pan** | SHIFT+drag panning, mouse wheel zooming, one-click fit-to-viewport |
| **Real-time Hover Info** | Live gene details with double-click hints on mouse hover |

---

### Target Gene Management

| Feature | Description |
|:--------|:------------|
| **File Import** | Load target gene lists from text files |
| **Manual Entry** | Type or paste gene names with smart parsing |
| **Checkbox Control** | Individual gene visibility toggles |
| **Smart Positioning** | Auto-prevent label overlaps with intelligent spacing |
| **Flexible Connections** | Configurable edge styles for clean, professional plots |

---

### Literature Mining Module

| Feature | Description |
|:--------|:------------|
| **PubTator3 Integration** | Semantic entity resolution with AI-powered named entity recognition across 36 million PubMed abstracts |
| **Dynamic Context Definition** | Autocomplete for Disease/Condition (required) and Treatment/Chemical (optional) with validated MeSH identifiers |
| **Relation Types Configuration** | Positive/negative correlation, stimulation, inhibition, or general association to match expression patterns |
| **Dual-API Strategy** | PubTator3 Relations API for Total and Context papers, NCBI E-utilities for Recent papers and PMIDs |
| **Disease Synonym Expansion** | Automatic inclusion of abbreviations and related terms for disease-specific searches |
| **Log-Scaled Scoring** | Weighted composite score preventing highly-studied genes from dominating results |
| **Clickable PMID Links** | Direct access to publications sorted by date (newest first) |

---

### Color Systems

| Style | Description |
|:------|:------------|
| **Gurzov Classic** | Traditional blue/red coloring for down/up-regulated genes |
| **MagmaFlow Classic 1** | P-value based gradients |
| **MagmaFlow Classic 2** | Log2 fold change based gradients |
| **MagmaFlow Classic 3** | Combined p-value and fold change gradients |
| **Viridis & Magma** | Professional scientific color palettes |
| **Custom Color Pickers** | Direct RGB/Hex color refinement |

---

### Precision Customization

| Feature | Description |
|:--------|:------------|
| **Independent Font Controls** | Separate sizes for title, axes, ticks, and annotations |
| **Dynamic Thresholds** | Real-time p-value and log2FC cutoff adjustment |
| **Smart Tick Spacing** | Auto or manual axis intervals for perfect scaling |
| **Advanced Dot Styling** | Opacity, outlines, sizes with live preview |
| **Publication Export** | High-res PNG (72-1200 DPI) and vector PDF |

---

### Project Workflow

| Feature | Description |
|:--------|:------------|
| **Complete Project Files** | Save/load all settings, annotations, and customizations |
| **Auto-save Tracking** | Visual indicators for unsaved changes |
| **Smart CSV Import** | Intelligent column mapping with auto-detection |
| **Session Persistence** | Remember your work across application restarts |

---

<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/01_MagmaFlow_Panel_git.jpeg" alt="MagmaFlow Interface Overview" width="1000"/>
</p>

<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/02_MagmaFlow_Panel_git.jpeg" alt="MagmaFlow Advanced Features" width="1000"/>
</p>

---

## Download

### Ready-to-Use Application

<p align="center">
  <a href="https://zenodo.org/records/17107683/files/MagmaFlow-10.0.3.dmg?download=1">
    <img src="https://img.shields.io/badge/Download-MagmaFlow%20v10.0.3%20for%20macOS-brightgreen?style=for-the-badge&logo=apple" alt="Download for macOS">
  </a>
</p>

### All Releases

Visit our [Zenodo repository](https://zenodo.org/records/17064020) for all versions and additional files.

> **Note:** MagmaFlow is distributed as a pre-built application only. Source code is not publicly available.

---

## Installation

| Step | Action |
|:----:|:-------|
| 1 | Download the DMG file from the link above |
| 2 | Open the DMG and drag MagmaFlow to Applications |
| 3 | Launch MagmaFlow from Applications folder |

---

## License

### MagmaFlow Academic Binary – End-User License Agreement (EULA)

**Version 3 – 2 October 2025**

By downloading, installing, or using the MagmaFlow executable (the "Software") you ("Licensee") agree to the terms of this EULA with the **Université libre de Bruxelles**, Avenue F. Roosevelt 50, B-1050 Brussels, Belgium ("ULB"), which owns all intellectual-property rights in the Software.

#### Definitions

| Term | Definition |
|:-----|:-----------|
| **Executable / Software** | The compiled distribution of MagmaFlow (DMG, MSI, JAR or similar) provided by ULB |
| **Academic Use** | Use by accredited higher-education or publicly funded research bodies, for teaching, research, or other not-for-profit scholarly purposes, with no direct or indirect commercial advantage |
| **Non-Commercial** | Any activity that is not primarily intended for commercial gain and is not carried out for the benefit of a for-profit entity |

#### License Grant

Subject to this EULA, ULB grants Licensee a **non-exclusive, non-transferable, royalty-free licence** to download, install, and run the Software solely for Academic and Non-Commercial purposes. No right to sublicense is granted.

#### Redistribution

Licensee may share an unmodified copy of the Executable with colleagues within Academic or publicly funded institutions provided that (a) this EULA accompanies every copy, and (b) the Citation is included. Publication of the Software or this EULA on public repositories, websites, or mirrors is prohibited.

#### Restrictions

Licensee shall not:
- Use the Software for any Commercial purpose without prior written consent from ULB
- Modify, adapt, translate, or create derivative works of the Software
- Reverse-engineer, decompile, or disassemble the Software except as permitted by applicable law
- Offer the Software to third parties as a hosted or cloud service (SaaS) or otherwise provide remote access

#### Ownership

The Software and all associated intellectual-property rights remain the exclusive property of ULB. No rights are granted other than those expressly stated herein.

#### Warranty Disclaimer

ULB provides the Software **"as is"** and disclaims all warranties, express or implied, including the warranties of merchantability, fitness for a particular purpose, and non-infringement.

#### Governing Law

This EULA is governed by Belgian law. Any dispute arising under it shall be submitted to the exclusive jurisdiction of the courts of Brussels.

---

**For Commercial licensing inquiries, please contact:** carlos.eduardo.buss@ulb.be

---

## Citation

If you use MagmaFlow in your research, please cite:

> **Buss C E et al.** MagmaFlow: An Interactive Volcano Plot Application for Differential Gene Expression Analysis. *FEBS Letters* (submitted). DOI: [10.5281/zenodo.17107683](https://zenodo.org/records/17107683)

---

## Contact

For questions, suggestions, or bug reports, please open an issue on GitHub.

For commercial licensing inquiries, please contact **carlos.eduardo.buss@ulb.be**.
