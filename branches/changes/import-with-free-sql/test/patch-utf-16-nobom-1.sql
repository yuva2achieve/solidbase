- - *   E N C O D I N G   " U T F - 1 6 L E "  
  
 - - *   / /   C o p y r i g h t   2 0 0 6   R e n �   M .   d e   B l o o i s  
  
 - - *   / /   L i c e n s e d   u n d e r   t h e   A p a c h e   L i c e n s e ,   V e r s i o n   2 . 0   ( t h e   " L i c e n s e " ) ;  
 - - *   / /   y o u   m a y   n o t   u s e   t h i s   f i l e   e x c e p t   i n   c o m p l i a n c e   w i t h   t h e   L i c e n s e .  
 - - *   / /   Y o u   m a y   o b t a i n   a   c o p y   o f   t h e   L i c e n s e   a t  
  
 - - *   / /           h t t p : / / w w w . a p a c h e . o r g / l i c e n s e s / L I C E N S E - 2 . 0  
  
 - - *   / /   U n l e s s   r e q u i r e d   b y   a p p l i c a b l e   l a w   o r   a g r e e d   t o   i n   w r i t i n g ,   s o f t w a r e  
 - - *   / /   d i s t r i b u t e d   u n d e r   t h e   L i c e n s e   i s   d i s t r i b u t e d   o n   a n   " A S   I S "   B A S I S ,  
 - - *   / /   W I T H O U T   W A R R A N T I E S   O R   C O N D I T I O N S   O F   A N Y   K I N D ,   e i t h e r   e x p r e s s   o r   i m p l i e d .  
 - - *   / /   S e e   t h e   L i c e n s e   f o r   t h e   s p e c i f i c   l a n g u a g e   g o v e r n i n g   p e r m i s s i o n s   a n d  
 - - *   / /   l i m i t a t i o n s   u n d e r   t h e   L i c e n s e .  
  
 - - *   / /   = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =  
  
 - - * 	 D E F I N I T I O N  
 - - * 	 	 U P G R A D E   " "   - - >   " 1 . 0 . 1 "  
 - - * 	 	 U P G R A D E   " 1 . 0 . 1 "   - - >   " 1 . 0 . 2 "  
 - - * 	 / D E F I N I T I O N  
  
  
  
  
  
  
  
 - - *   / /   = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =  
 - - *   U P G R A D E   " "   - - >   " 1 . 0 . 1 "  
 - - *   / /   = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =  
  
 - - *   S E C T I O N   " C r e a t i n g   t a b l e   D B V E R S I O N "  
 C R E A T E   T A B L E   D B V E R S I O N  
 (    
 	 V E R S I O N   V A R C H A R ,    
 	 T A R G E T   V A R C H A R ,    
 	 S T A T E M E N T S   I N T E G E R   N O T   N U L L    
 ) ;  
  
 - - *   / /   T h e   p a t c h   t o o l   e x p e c t s   t o   b e   a b l e   t o   u s e   t h e   D B V E R S I O N   t a b l e   a f t e r   t h e   * f i r s t *   s q l   s t a t e m e n t  
  
 - - *   S E C T I O N   " C r e a t i n g   t a b l e   D B V E R S I O N L O G "  
 C R E A T E   T A B L E   D B V E R S I O N L O G  
 (  
 	 I D   I N T E G E R   I D E N T I T Y ,   - -   A n   i n d e x   m i g h t   b e   n e e d e d   h e r e   t o   l e t   t h e   i d e n t i t y   p e r f o r m  
 	 S O U R C E   V A R C H A R ,  
 	 T A R G E T   V A R C H A R   N O T   N U L L ,  
 	 S T A T E M E N T   V A R C H A R   N O T   N U L L ,  
 	 S T A M P   T I M E S T A M P   N O T   N U L L ,  
 	 C O M M A N D   V A R C H A R ,  
 	 R E S U L T   V A R C H A R  
 ) ;  
  
 - - *   / /   T h e   e x i s t e n c e   o f   D B V E R S I O N L O G   w i l l   a u t o m a t i c a l l y   b e   d e t e c t e d   a t   t h e   e n d   o f   t h i s   p a t c h  
  
 - - *   / U P G R A D E  
  
  
  
  
  
  
  
 - - *   / /   = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =  
 - - *   U P G R A D E   " 1 . 0 . 1 "   - - >   " 1 . 0 . 2 "  
 - - *   / /   = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =  
  
 - - *   S E C T I O N   " C r e a t i n g   t a b l e   U S E R S "  
 C R E A T E   T A B L E   U S E R S  
 (  
 	 U S E R _ I D   I N T   I D E N T I T Y ,  
 	 U S E R _ U S E R N A M E   V A R C H A R   N O T   N U L L ,  
 	 U S E R _ P A S S W O R D   V A R C H A R   N O T   N U L L  
 ) ;  
  
 - - *   S E C T I O N   " I n s e r t i n g   a d m i n   u s e r "  
 I N S E R T   I N T O   U S E R S   (   U S E R _ U S E R N A M E ,   U S E R _ P A S S W O R D   )   V A L U E S   (   ' r e n � ' ,   ' 0 D P i K u N I r r V m D 8 I U C u w 1 h Q x N q Z c = '   ) ;  
  
 - - *   / U P G R A D E  
  
 - - *   / /   = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =  
  
 