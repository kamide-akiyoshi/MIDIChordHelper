package camidion.chordhelper.chordmatrix;

import java.awt.Color;

/**
 * 色セット（ダークモード切替対応）
 */
public class ColorSet {
	Color[] focus = new Color[2];	// 0:lost 1:gained
	Color[] foregrounds = new Color[2];	// 0:unselected 1:selected
	public Color[] backgrounds = new Color[4]; // 0:remote 1:left 2:local 3:right
	Color[] indicators = new Color[3];	// 0:natural 1:sharp 2:flat
}
