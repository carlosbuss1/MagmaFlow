<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/MagmaFlow_header.png" alt="MagmaFlow Header" width="1000"/>
</p>

# MagmaFlow

**An Interactive Volcano Plot Application for Differential Gene Expression Analysis**

<p align="center">
  <img src="https://github.com/carlosbuss1/MagmaFlow/blob/main/MagmaFlow_Panel_git.png" alt="MagmaFlow Interface" width="1000"/>
</p>

## Overview
MagmaFlow is a powerful, user-friendly JavaFX application designed for creating interactive volcano plots from differential gene expression data. It provides researchers with an intuitive interface to visualize statistical significance versus fold change patterns in genomic datasets.

## Key Features

### Advanced Color Schemes
- **Gurzov Classic Style**: Traditional blue/red coloring for down/up-regulated genes
- **MagmaFlow Classic Styles**: Customizable gradient palettes with direct color refinement  
  - Classic 1: P-value based gradients  
  - Classic 2: Log2 fold change based gradients  
  - Classic 3: Combined p-value and fold change gradients  
- **MagmaFlow Viridis & Magma Styles**: Professional scientific color palettes  
- **Custom Color Pickers**: Direct access to RGB/Hex color refinement without palette selection  

## Installation & Usage

```bash
# Clone the repository
git clone https://github.com/carlosbuss1/MagmaFlow.git

# Navigate to project directory
cd MagmaFlow

# Compile and run
mvn clean javafx:run

