package com.example.worldborderevent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client radius easing plus subtle event particles near the active wall. */
@Mod.EventBusSubscriber(modid=WorldBorderEventMod.MODID,value=Dist.CLIENT)
public final class ClientBorderState {
 private static volatile double radius=10000,targetRadius=10000;
 private static volatile boolean active=false;
 private static int particleClock=0;
 private ClientBorderState(){}
 public static void setRadius(double next){targetRadius=Math.max(1,next);active=Math.abs(targetRadius-radius)>0.02;}
 public static boolean isEventActive(){return active;}
 @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;
  double d=targetRadius-radius;
  if(Math.abs(d)<0.03){radius=targetRadius;active=false;}else radius+=d*0.22;
  Minecraft mc=Minecraft.getInstance(); Level l=mc.level;
  if(l==null||mc.player==null||(!active))return;
  if(++particleClock%8!=0)return;
  double r=getRadius(l.dimension()==Level.NETHER),px=mc.player.getX(),pz=mc.player.getZ();
  double x=Math.abs(px)>Math.abs(pz)?Math.copySign(r,px):px;
  double z=Math.abs(pz)>=Math.abs(px)?Math.copySign(r,pz):pz;
  for(int i=0;i<2;i++) l.addParticle(ParticleTypes.ELECTRIC_SPARK,x+(l.random.nextDouble()-.5)*.6,mc.player.getY()+l.random.nextDouble()*2.0,z+(l.random.nextDouble()-.5)*.6,0,(l.random.nextDouble()-.5)*.04,0);
 }
 public static double getRadius(boolean nether){double r=radius;return nether?r/8:r;}
}
