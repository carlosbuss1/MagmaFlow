<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/MagmaFlow_header.png" alt="MagmaFlow Header" width="1000"/>
</p>

# MagmaFlow
**An Interactive Volcano Plot Application for Differential Gene Expression Analysis**

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-17+-blue.svg)](https://openjfx.io/)
[![License](https://img.shields.io/badge/License-GPL%20v3-red.svg)](LICENSE)
[![DOI](https://img.shields.io/badge/DOI-10.5281%2Fzenodo.17011629-blue.svg)](https://zenodo.org/records/17011629)

## Overview

MagmaFlow is a powerful, user-friendly JavaFX application designed for creating interactive volcano plots from differential gene expression data. It provides researchers with an intuitive interface to visualize statistical significance versus fold change patterns in genomic datasets with publication-ready quality.

<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/MagmaFlow_Panel_git.png" alt="MagmaFlow Interface" width="1000"/>
</p>

## Key Features

### Interactive Superpowers
- **Double-Click Gene Selection**: Instantly add/remove target genes by double-clicking data points
- **Drag & Drop Labels**: Reposition gene annotations by dragging them anywhere
- **Smart Edge Connections**: Choose how annotation lines connect (left, right, center, smart, auto)
- **Advanced Zoom & Pan**: SHIFT+drag panning, mouse wheel zooming, one-click fit-to-viewport
- **Real-time Hover Info**: Live gene details with double-click hints on mouse hover

### Smart Target Gene Management
- **File Import**: Load target gene lists from text files
- **Manual Entry**: Type or paste gene names with smart parsing
- **Checkbox Control**: Individual gene visibility toggles
- **Smart Positioning**: Auto-prevent label overlaps with intelligent spacing
- **Flexible Connections**: Configurable edge styles for clean, professional plots

### Revolutionary Color Systems
- **Gurzov Classic Style**: Traditional blue/red coloring for down/up-regulated genes
- **MagmaFlow Classic Styles**: Customizable gradient palettes with **direct color refinement**
  - **Classic 1**: P-value based gradients
  - **Classic 2**: Log2 fold change based gradients  
  - **Classic 3**: Combined p-value and fold change gradients
- **MagmaFlow Viridis & Magma Styles**: Professional scientific color palettes
- **Custom Color Pickers**: **Skip palette selection** - go directly to RGB/Hex refinement

### Precision Customization Engine
- **Independent Font Controls**: Separate sizes for title, axes, ticks, and annotations
- **Dynamic Thresholds**: Real-time p-value and log2FC cutoff adjustment
- **Smart Tick Spacing**: Auto or manual axis intervals for perfect scaling
- **Advanced Dot Styling**: Opacity, outlines, sizes with live preview
- **Publication Export**: High-res PNG (72-1200 DPI) and vector PDF

### Project Workflow Management
- **Complete Project Files**: Save/load all settings, annotations, and customizations
- **Auto-save Tracking**: Visual indicators for unsaved changes
- **Smart CSV Import**: Intelligent column mapping with auto-detection
- **Session Persistence**: Remember your work across application restarts

## Installation & Quick Start

### Prerequisites
- **Java 17+** (OpenJDK recommended)
- **Maven 3.6+** (for building)
- **JavaFX Runtime** (included in most Java distributions)

### Lightning-Fast Setup
```bash
# Clone the repository
git clone https://github.com/carlosbuss1/MagmaFlow.git

# Navigate to project directory
cd MagmaFlow

# Build and launch in one command
mvn clean javafx:run

