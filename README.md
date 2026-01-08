<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/MagmaFlow_header.png" alt="MagmaFlow Header" width="1000"/>
</p>
MagmaFlow
An Interactive Volcano Plot Application for Differential Gene Expression Analysis
Overview
MagmaFlow is a powerful, user-friendly JavaFX application designed for creating interactive volcano plots from differential gene expression data. It provides researchers with an intuitive interface to visualize statistical significance versus fold change patterns in genomic datasets with publication-ready quality.
Key Features
Interactive Superpowers

Double-Click Gene Selection: Instantly add/remove target genes by double-clicking data points
Drag & Drop Labels: Reposition gene annotations by dragging them anywhere
Smart Edge Connections: Choose how annotation lines connect (left, right, center, smart, auto)
Advanced Zoom & Pan: SHIFT+drag panning, mouse wheel zooming, one-click fit-to-viewport
Real-time Hover Info: Live gene details with double-click hints on mouse hover

Target Gene Management

File Import: Load target gene lists from text files
Manual Entry: Type or paste gene names with smart parsing
Checkbox Control: Individual gene visibility toggles
Smart Positioning: Auto-prevent label overlaps with intelligent spacing
Flexible Connections: Configurable edge styles for clean, professional plots

Literature Mining Module

PubTator3 Integration: Semantic entity resolution with AI-powered named entity recognition across 36 million PubMed abstracts
Dynamic Context Definition: Autocomplete for Disease/Condition (required) and Treatment/Chemical (optional) with validated MeSH identifiers
Relation Types Configuration: Positive/negative correlation, stimulation, inhibition, or general association to match expression patterns
Dual-API Strategy: PubTator3 Relations API for Total and Context papers, NCBI E-utilities for Recent papers and PMIDs
Disease Synonym Expansion: Automatic inclusion of abbreviations and related terms for disease-specific searches
Log-Scaled Scoring: Weighted composite score preventing highly-studied genes from dominating results
Clickable PMID Links: Direct access to publications sorted by date (newest first)

Revolutionary Color Systems

Gurzov Classic Style: Traditional blue/red coloring for down/up-regulated genes
MagmaFlow Classic Styles: Customizable gradient palettes with direct color refinement

Classic 1: P-value based gradients
Classic 2: Log2 fold change based gradients
Classic 3: Combined p-value and fold change gradients


MagmaFlow Viridis & Magma Styles: Professional scientific color palettes
Custom Color Pickers: Skip palette selection - go directly to RGB/Hex refinement

Precision Customization Engine

Independent Font Controls: Separate sizes for title, axes, ticks, and annotations
Dynamic Thresholds: Real-time p-value and log2FC cutoff adjustment
Smart Tick Spacing: Auto or manual axis intervals for perfect scaling
Advanced Dot Styling: Opacity, outlines, sizes with live preview
Publication Export: High-res PNG (72-1200 DPI) and vector PDF

Project Workflow Management

Complete Project Files: Save/load all settings, annotations, and customizations
Auto-save Tracking: Visual indicators for unsaved changes
Smart CSV Import: Intelligent column mapping with auto-detection
Session Persistence: Remember your work across application restarts

<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/01_MagmaFlow_Panel_git.jpeg" alt="MagmaFlow Interface Overview" width="1000"/>
</p>
<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/02_MagmaFlow_Panel_git.jpeg" alt="MagmaFlow Advanced Features" width="1000"/>
</p>
Download
Ready-to-Use Application

MagmaFlow v10.0.3 for macOS (DMG) - Interactive Volcano Plot Application

All Releases
Visit our Zenodo repository for all versions and additional files.

Note: MagmaFlow is distributed as a pre-built application only. Source code is not publicly available.

Installation

Download the DMG file from the link above
Open the DMG and drag MagmaFlow to Applications
Launch MagmaFlow from Applications folder

License
MagmaFlow is distributed under a Non-Commercial End User License Agreement (EULA) provided by Université libre de Bruxelles (ULB). The software is free for academic and non-commercial research use. Commercial use requires a separate license agreement.
For commercial licensing inquiries, please contact ULB Technology Transfer Office.
Citation
If you use MagmaFlow in your research, please cite:

Buss C, et al. (2025). MagmaFlow: An Interactive Volcano Plot Application for Differential Gene Expression Analysis. FEBS Letters (submitted)

Contact
For questions, suggestions, or bug reports, please open an issue on GitHub.


