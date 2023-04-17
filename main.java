/*     KEYMAP
    ------------
    LEFT CLICK -> PAINT the grid with the specified element on the top left corner
    UP ARROW & DOWN ARROW     -> INCREASE and DECREASE cursor size respectively
    RIGHT ARROW & LEFT ARROW  -> cyce cursor type FORWARD and BACKWARD respectively
    G -> toggle GRIDLINES
    C -> CLEAR the grid
    SPACE BAR -> PAUSE the simulation
    S -> STEP the simulation if paused
    0-6 -> ELEMENT SELECT (each number corresponds to the index of an element in the elementNames array)
*/

final int scale = 5;
final int settingsHeight = 100;
final float mx_offset = 0;
final float my_offset = 0;

int[][] world; // The 2D array containing every element on the screen
float centerx, centery; // The center x and y coordinates of the window
int cols, rows; // Number of columns and rows on the world grid
int mc, mr, _mc, _mr; // Current and previous mouse positions on the grid

byte cursorType = 0;
int cursorSize = 5;
color cursorColor = color(255, 255, 255, 120);

int selectedElement = 1;
final String[] elementNames = {
  "Air",
  "Sand",
  "Water",
  "Wood",
  "Stone",
  "Steam",
  "Acid", // TODO - CORROSION
};
final color[] elementColors = {
  color(0,0,0,1),        // Air
  color(194, 178, 128),   // Sand
  color(100, 100, 200, 150),     // Water
  color(95, 60, 35),    // Wood
  color(80, 80, 80),      // Stone
  color(150, 150, 150, 125),   // Steam
  color(100, 170, 100),  // Acid
};
/* Bitmaps of element types */
int solidElements      = 0b0101100;
int liquidElements     = 0b0010001;
int gasElements        = 0b0000010;
int flammableElements  = 0b0011000;
int corrodableElements = 0b0111100;

int lastUpdate = 0;
boolean showGrid = false;
boolean paused = false;
boolean elementSelect = false;

void setup() {
  size(1350, 740);
  centerx = width/2;
  centery = height/2;
  cols = (width)/scale;
  rows = (height)/scale;
  world = new int[cols][rows];
}

void draw() {
  background(40);
  
  // Render gridlines
  if (showGrid) {
    stroke(80);
  } else {
    noStroke();
  }
  
  // Render cells
  for (int col = 0; col < cols; col++) {
    for (int row = 0; row < rows; row++) {
      int elem = world[col][row];
      pushStyle();
      fill(elementColors[elem]);
      rect(col*scale, row*scale, scale, scale);
      popStyle();
    }
  }
  
  // Update cells
  if (!paused || elementSelect)
    updateCells();
  
  if (paused) {
    // Draw pause icon
    pushStyle();
    fill(225);
    rect(width-90, 40, 15, 65);
    rect(width-50, 40, 15, 65);
    popStyle();
  }
  
  // Record the current column and row the mouse is on
  mc = (int)(mouseX+mx_offset)/scale;
  mr = (int)(mouseY+my_offset)/scale;
  
  // Visual aids for different cursor types
  pushStyle();
  fill(cursorColor);
  if (cursorType == 1) { // Rectangular
    for (int o = 1; o < cursorSize*2; o++) {
      rect((mc-cursorSize)*scale, (mr+cursorSize-o)*scale, scale, scale);
      rect((mc+cursorSize)*scale, (mr+cursorSize-o)*scale, scale, scale);
      rect((mc-cursorSize+o)*scale, (mr-cursorSize)*scale, scale, scale);
      rect((mc-cursorSize+o)*scale, (mr+cursorSize)*scale, scale, scale);
    }
  } else if (cursorType == 2) { // Circular
    for (int theta = 0; theta < 360; theta+=scale) {
      stroke(255);
      line(mouseX, mouseY, mouseX+5*cursorSize*cos(radians(theta)), mouseY+5*cursorSize*sin(radians(theta)));
    }
  }
  popStyle();
  
  // Cursor drawing
  if (mousePressed && inBounds(mc, mr)) {
    if (cursorType == 0) { // Pinpoint drawing
      putElementI(mc, mr, _mc, _mr, selectedElement);
    } else if (cursorType == 1) { // Rectangular fill
      for (int o = 1; o < cursorSize*2; o++)
        for (int k = 1; k < cursorSize*2; k++)
          putElementI(mc-cursorSize+o, mr+cursorSize-k, _mc-cursorSize+o, _mr+cursorSize-k, selectedElement);
    } else if (cursorType == 2) { // Circular fill
      // TODO
    }
  }
  
  _mc = mc;
  _mr = mr;
  
  // UI
  pushStyle();
  stroke(150);
  strokeWeight(10);
  //rect(centerx-150, centery-50, 300, 100);
  popStyle();
  
  // debug info
  fill(35);
  fill(200);
  push();
  translate(20, 25);
  text(String.format("FPS: %d\n(mc,mr): (%d,%d)\nCt: %d\nCs: %d\nP: "+paused, (int)frameRate, mc, mr, cursorType, cursorSize), 0, 0);
  fill(elementColors[selectedElement]);
  text(elementNames[selectedElement], 0, 100);
  pop();
}

void keyPressed() {
  if (key == CODED) {
    if (keyCode == UP) { // Increase cursor size
      cursorSize++;
    } else if (keyCode == DOWN) { // Decrease cursor size
      cursorSize = cursorSize > 1 ? cursorSize-1 : 1;
    } else if (keyCode == RIGHT) { // Cycle cursor type forward
      cursorType = (byte)(cursorType < 128 ? cursorType+1 : 128);
    } else if (keyCode == LEFT) { // Cycle cursor type backward
      cursorType = (byte)(cursorType > 0 ? cursorType-1 : 0);
    }
  } else {
    if (key == 'c' || key == 'C') { // Clear the world (fill with air)
      for (int c = 0; c < cols; c++)
        for (int r = 0; r < rows; r++)
          world[c][r] = 0;
    } else if (key == 'g' || key == 'G') { // Show/hide grid lines
      showGrid = !showGrid;
    } else if (key == 's' || key == 'S') { // Step the simulation one frame
      if (paused)
        updateCells();
    } else if (key == ' ') { // Pause the simulation
      paused = !paused;
    } else if (key > 47 && key < 58) { // Element selection
      // 48 is the ASCII code for 0 and 57 is the code for 9
      selectedElement = key - 48;
    }
  }
}

// Update the world from the bottom-up for elements that fall downward and top-down for elements that "fall" upward 
void updateCells() {
  for (int col = 0; col < cols; col++) {
    for (int row = rows-1; row >= 0; row--) {
      int elem = world[col][row];
      switch(elem) {
        case 0: // Air
          break;
        case 1: // Sand
          updateGranularSolid(col, row);
          break;
        case 2: // Water
          updateBasicLiquid(col, row, 10);
          break;
        case 3: // Wood
          break;
        case 4: // Stone
          updateStackableSolid(col, row);
          break;
        case 6: // Acid
          updateBasicLiquid(col, row, 5);
          break;
      }
    }
  }
  
  for (int col = 0; col < cols; col++) {
    for (int row = 0; row < rows; row++) {
      int elem = world[col][row];
      switch(elem) {
        case 5: // Steam
          updateBasicGas(col, row, 10);
          break;
      }
    }
  }
}

/** Helper Methods **/
// Clamp a number between a minimum and maximum value
int clamp(int val, int min, int max)         { return min(max(val, min), max); }
float clamp(float val, float min, float max) { return min(max(val, min), max); }
// Generates a random number that is either -1 or 1
int randomSign() { return ((int)random(2) == 0 ? -1 : 1); }
// Generates a random number that is either -1, 0, or 1
int randomSignZ() { return (int)random(-2,2); }

// Round a float depending on the tenths place
int roundFloat(float val) {
  int tenths = (int)(val*10)%10;
  int v = (int)val;
  return (tenths >= .5 ? v+1 : v);
}

// Given a column and row, check if it's inside the grid
boolean inBounds(int c, int r) {
  return (c >= 0 && c < cols && r >= 0 && r < rows);
}

// Sets the element at the given column and row to be the given element
void putElement(int c, int r, int element) {
  if (inBounds(c, r))
    world[c][r] = element;
}

// putElement but with interpolation (I) between two points on the grid
void putElementI(int c, int r, int _c, int _r, int element) {
  putElement(c, r, element);
  //println(String.format("(%d, %d) previously (%d, %d) %d", c, r, _c, _r, (c == _c && r == _r) ? 1 : 0));
  float dy = r-_r;
  float dx = c-_c;
  float m = dy/dx;
  
  // Check if it is a vertical line or otherwise and fill in the gaps using the y=mx+b line formula
  if (Float.isInfinite(m) || m != m /*NaN check*/) {
    for (int y = (r < _r ? r : _r); y < (r < _r ? _r : r); y++) {
      putElement(c, y, element);
    }
  } else {
    for (float x = (c < _c ? c : _c); x < (c < _c ? _c : c); x+=0.1f) {
      int y = roundFloat(m*(x-c)+r);
      putElement(roundFloat(x), y, element);
    }
  }
}

// Swap two cells on the world grid
void swap(int c1, int r1, int c2, int r2) {
  if (inBounds(c1, r1) && inBounds(c2, r2)) {
    int temp = world[c1][r1];
    world[c1][r1] = world[c2][r2];
    world[c2][r2] = temp;
  }
}

// Given a column and a row, check if it's a certain element
boolean isA(int c, int r, int element) {
  return (inBounds(c, r) ? world[c][r] == element : false);
}

// Given a column and a row, check if it's a certain element type by bitmasking
boolean isTypeOf(int c, int r, int elementType) {
  c = clamp(c, 0, cols-1);
  r = clamp(r, 0, rows-1);
  /*if (!inBounds(c, r))
    return false;*/
  int element = world[c][r];
  return ((1 << elementColors.length-element-1 & elementType) != 0);
}

/* Element type shorthand checks */
boolean isAir(int c, int r) { return isA(c, r, 0); }
boolean isSolid(int c, int r) { return isTypeOf(c, r, solidElements); }
boolean isLiquid(int c, int r) { return isTypeOf(c, r, liquidElements); }
boolean isGas(int c, int r) { return isTypeOf(c, r, gasElements); }

/** Update Methods **/
// Solids that have a similar behavior to sand
void updateGranularSolid(int c, int r) {
  if (isGas(c, r+1) || isLiquid(c, r+1) || isAir(c, r+1)) {
    swap(c, r, c, r+1);
  } else {
    int cdiff = randomSign();
    if ((isGas(c+cdiff, r+1) || isLiquid(c+cdiff, r+1)) || isAir(c+cdiff ,r+1)) {
      swap(c, r, c+cdiff, r+1);
    }
  }
}

// Solids that only stack on top of each other
void updateStackableSolid(int c, int r) {
  if (isAir(c, r+1) || isGas(c, r+1) || isLiquid(c, r+1)) {
    swap(c, r, c, r+1);
  }
}

// Universal liquid updater  
void updateBasicLiquid(int c, int r, int dispersion) {
  if (isAir(c, r+1) || isGas(c, r+1)) {
    swap(c, r, c, r+1);
  } else {
    // Dispersion algorithm
    int self = world[c][r];
    int ddiff = randomSign();
    for (int d = 1; d <= dispersion; d++) {
      if (isA(c+d*ddiff, r, self)) continue;
      if (isAir(c+d*ddiff, r) || isGas(c+d*ddiff, r)) {
        swap(c, r, c+d*ddiff, r);
      } else {
        break;
      }
    }
  }
}

// Universal gas updater
void updateBasicGas(int c, int r, int dispersion) {
  int cdiff = randomSignZ();
  if (isAir(c+cdiff, r-1)) {
    swap(c, r, c+cdiff, r-1);
  } else {
    int self = world[c][r];
    int ddiff = randomSign();
    for (int d = 1; d <= dispersion; d++) {
      if (isA(c+d*ddiff, r, self)) continue;
      if (isAir(c+d*ddiff, r)) {
        swap(c, r, c+d*ddiff, r);
      } else {
        break;
      }
    }
  }
}
